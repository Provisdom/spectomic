(ns provisdom.spectomic.core
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as sgen]
    [provisdom.spectomic.specs :as spectomic])
  (:import (clojure.lang ExceptionInfo)))

;; this could be a multimethod?
(def ^:private class->datomic-type
  {java.lang.String     :db.type/string
   java.lang.Boolean    :db.type/boolean
   java.lang.Double     :db.type/double
   java.lang.Long       :db.type/long
   java.lang.Float      :db.type/float
   java.util.Date       :db.type/instant
   java.util.UUID       :db.type/uuid
   java.math.BigDecimal :db.type/bigdec
   java.math.BigInteger :db.type/bigint
   java.net.URI         :db.type/uri
   clojure.lang.Keyword :db.type/keyword})

(def datomic-types (conj (set (vals class->datomic-type)) :db.type/bytes :db.type/ref))

(defn datomic-type?
  [x]
  "Returns true if `x` is a datomic type."
  (some? (datomic-types x)))

(defn- obj->datomic-type
  [obj custom-type-resolver]
  (let [t (type obj)]
    (cond
      (contains? class->datomic-type t) (class->datomic-type t)
      (map? obj) :db.type/ref
      (nil? obj) nil
      (= (Class/forName "[B") (.getClass obj)) :db.type/bytes
      :else (custom-type-resolver obj))))

(defn sample-types
  "Returns a set of all the datomic types `samples` contains."
  [samples custom-type-resolver]
  (into #{}
        (comp
          (map (fn [sample]
                 (if (or (sequential? sample) (set? sample))
                   ::cardinality-many
                   (obj->datomic-type sample custom-type-resolver))))
          ;; we need to remove nils for the cases where a spec is nilable.
          (filter some?))
        samples))

(defn spec-form
  [keyword-or-form]
  (if (keyword? keyword-or-form)
    (s/form keyword-or-form)
    keyword-or-form))

(defn find-type-via-generation
  "Returns Datomic schema for `spec`. Takes a map of the following options:
    :custom-type-resolver - A function that takes an object and returns the Datomic value type.
    :value-type - Force the value type for a spec to be this type. This is useful
                  for cases where your spec generates multiple types and your code
                  will work with multiple types (i.e. long and double)."
  [spec {:keys [custom-type-resolver value-type gen-resize]
         :or   {gen-resize 10}}]
  (let [g (sgen/such-that (fn [s]
                            ;; we need a sample that is not nil
                            (and (some? s)
                                 ;; if the sample is a collection, then we need a collection that is not empty.
                                 ;; we cannot generate Datomic schema with an empty collection.
                                 (if (coll? s)
                                   (not-empty s)
                                   true)))
                          (s/gen spec))
        samples (binding [s/*recursion-limit* 1]
                  (try
                    (doall (sgen/sample ((resolve 'clojure.test.check.generators/resize) gen-resize g) 100))
                    (catch ExceptionInfo ex
                      (throw (ex-info (.getMessage ex) (merge (ex-data ex)
                                                              {:spec spec}))))))
        types (sample-types samples custom-type-resolver)
        cardinality-many? (= #{::cardinality-many} types)]
    ;; if we already know the value type, we don't need to do anything else
    (if value-type
      {:db/valueType   value-type
       :db/cardinality (if cardinality-many? :db.cardinality/many :db.cardinality/one)}

      ;; Given we don't know the value type, this makes sure we are getting
      ;; consistent types from the generator. If types are inconsistent then schema
      ;; generation is unclear.
      (cond
        (> (count types) 1) (throw (ex-info "Spec resolves to multiple types." {:spec spec :types types}))
        (empty? types) (throw (ex-info "No matching Datomic types." {:spec spec}))
        :else
        (let [t (first types)]
          (cond
            cardinality-many? (let [collection-types (sample-types (mapcat identity samples) custom-type-resolver)]
                                (cond
                                  (> (count collection-types) 1)
                                  (throw (ex-info "Spec collection contains multiple types."
                                                  {:spec spec :types collection-types}))
                                  (= ::cardinality-many (first collection-types))
                                  (throw (ex-info "Cannot create schema for a collection of collections."
                                                  {:spec spec}))
                                  :else {:db/valueType   (first collection-types)
                                         :db/cardinality :db.cardinality/many}))
            (datomic-type? t) {:db/valueType   t
                               :db/cardinality :db.cardinality/one}
            :else (throw (ex-info "Invalid Datomic type." {:spec spec :type t}))))))))

(declare spec->datomic-schema)

(defn find-type-via-form
  ([spec] (find-type-via-form spec nil))
  ([spec custom-type-resolver]
   (let [form (spec-form spec)]
     (when (sequential? form)
       (case (first form)
         (clojure.spec.alpha/keys clojure.spec.alpha/merge)
         {:db/valueType   :db.type/ref
          :db/cardinality :db.cardinality/one}

         (clojure.spec.alpha/coll-of
           clojure.spec.alpha/every)
         (let [inner-type (spec->datomic-schema (eval (second form)) {:custom-type-resolver custom-type-resolver})]
           (when (= :db.cardinality/one (:db/cardinality inner-type))
             {:db/cardinality :db.cardinality/many
              :db/valueType   (:db/valueType inner-type)}))

         clojure.spec.alpha/and
         (let [inner-forms (rest form)
               found-schemas (reduce (fn [schemas form]
                                       (if-let [schema (find-type-via-form form)]
                                         (conj schemas schema)
                                         schemas))
                                     [] inner-forms)]
           ;; if we found multiple schemas this likely means that the Spec represents
           ;; multiple types. We'll let the find-type-via-generation throw the exception.
           (if (= 1 (count found-schemas))
             (first found-schemas)
             nil))

         clojure.spec.alpha/nilable
         (spec->datomic-schema (eval (second form)))

         nil)))))

(defn- spec->datomic-schema
  "Returns Datomic schema for `spec`."
  ([spec] (spec->datomic-schema spec nil))
  ([spec {:keys [custom-type-resolver] :as lookup-opts}]
   (if-let [t (find-type-via-form spec custom-type-resolver)]
     t
     (find-type-via-generation spec lookup-opts))))

(defn- spec-and-data
  [s]
  (let [c (s/conform ::spectomic/schema-entry s)]
    (if (= ::s/invalid c)
      (throw (ex-info "Invalid schema entry" {:data s}))
      (let [[b s] c]
        (condp = b
          :att [s {}]
          :att-and-schema s)))))

(defn datomic-schema
  ([specs] (datomic-schema specs nil))
  ([specs {:keys [custom-type-resolver]
           :or   {custom-type-resolver type}}]
   (into []
         (map (fn [s]
                (let [[spec extra-schema] (spec-and-data s)
                      base-schema (if (and (:db/valueType extra-schema)
                                           (:db/cardinality extra-schema))
                                    ;; if the user explicitly specifies the type and cardinality,
                                    ;; we can skip the call to spec->datomic-schema
                                    {}
                                    (spec->datomic-schema spec {:custom-type-resolver custom-type-resolver
                                                                :value-type           (:db/valueType extra-schema)}))]
                  (merge {:db/ident spec}
                         base-schema
                         extra-schema))))
         specs)))

(s/fdef datomic-schema
  :args (s/cat :specs
               (s/coll-of
                 (s/or :spec qualified-keyword?
                       :tuple (s/tuple qualified-keyword? ::spectomic/datomic-optional-field-schema)))
               :opts (s/? (s/nilable ::spectomic/schema-options)))
  :ret ::spectomic/datomic-field-schema)

(defn datascript-schema-from-datomic-schema
  [datomic-schema]
  (reduce (fn [ds-schema schema]
            (assoc ds-schema
              (:db/ident schema)
              (let [schema (select-keys schema [:db/cardinality :db/unique :db/valueType :db/isComponent])]
                ;; only include :db/valueType when it is a ref.
                (if (= :db.type/ref (:db/valueType schema))
                  schema
                  (dissoc schema :db/valueType)))))
          {} datomic-schema))

(defn datascript-schema
  ([specs] (datascript-schema specs nil))
  ([specs opts]
   (let [s (datomic-schema specs opts)]
     (datascript-schema-from-datomic-schema s))))

(s/fdef datascript-schema
  :args (s/cat :specs
               (s/coll-of
                 (s/or :spec qualified-keyword?
                       :tuple (s/tuple qualified-keyword? ::spectomic/datascript-optional-field-schema)))
               :opts (s/? (s/nilable ::spectomic/schema-options)))
  :ret ::spectomic/datascript-schema)