(ns provisdom.spectomic.core
  (:require
    [clojure.spec :as s]
    [clojure.spec.gen :as sgen]
    [provisdom.spectomic.specs :as spectomic]))

(alias 'tgen 'clojure.test.check.generators)

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

(defn- obj->datomic-type
  [obj]
  (let [t (type obj)]
    (cond
      (contains? class->datomic-type t) (class->datomic-type t)
      (map? obj) :db.type/ref
      (coll? obj) :coll
      (nil? obj) nil
      (= (Class/forName "[B") (.getClass obj)) :db.type/bytes
      :else (type obj))))

(defn sample-types
  "Returns a set of all the datomic types `samples` contains."
  [samples]
  (into #{}
        (comp
          (map obj->datomic-type)
          (filter some?))
        samples))

(defn- spec->datomic-schema
  "Returns Datomic schema for `spec`."
  [spec]
  (let [g (sgen/such-that (fn [s]
                            ;; we need a sample that is not nil
                            (and (some? s)
                                 ;; if the sample is a collection, then we need a collection that is not empty.
                                 ;; we cannot generate Datomic schema with an empty collection.
                                 (if (coll? s)
                                   (not-empty s)
                                   true))) (s/gen spec))
        samples (binding [s/*recursion-limit* 1]
                  (sgen/sample ((resolve `tgen/resize) 10 g) 100))
        types (sample-types samples)]
    ;; Makes sure we are getting consistent types from the generator. If types are inconsistent then schema
    ;; generation is unclear.
    (if (> (count types) 1)
      (throw (ex-info "Spec resolves to multiple types." {:spec spec :types types}))
      (if (empty? types)
        (throw (ex-info "No matching Datomic types." {:spec spec}))
        (let [t (first types)]
          (if (= t :coll)
            (let [collection-types (sample-types (mapcat identity samples))]
              (cond
                (> (count collection-types) 1)
                (throw (ex-info "Spec collection contains multiple types." {:spec spec :types collection-types}))
                (= :coll (first collection-types))
                (throw (ex-info "Cannot create schema for a collection of collections." {:spec spec}))
                :else {:db/valueType   (first collection-types)
                       :db/cardinality :db.cardinality/many}))
            {:db/valueType   t
             :db/cardinality :db.cardinality/one}))))))

(defn- spec-and-data
  [s]
  (if (vector? s)
    s
    [s {}]))

(defn datomic-schema*
  [specs]
  (into []
        (map (fn [s]
               (let [[spec extra-schema] (spec-and-data s)]
                 (merge
                   (assoc (spec->datomic-schema spec)
                     :db/ident spec)
                   extra-schema))))
        specs))

(s/fdef datomic-schema*
        :args (s/cat :specs
                     (s/coll-of
                       (s/or :spec qualified-keyword?
                             :tuple (s/tuple qualified-keyword? ::spectomic/datomic-optional-field-schema))))
        :ret ::spectomic/datomic-field-schema)

(defmacro datomic-schema
  [& specs]
  (datomic-schema* specs))

(defn datascript-schema*
  [specs]
  (let [s (datomic-schema* specs)]
    (reduce (fn [ds-schema schema]
              (assoc ds-schema
                (:db/ident schema)
                (dissoc schema :db/ident)))
            {} s)))

(s/fdef datascript-schema*
        :args (s/cat :specs
                     (s/coll-of
                       (s/or :spec qualified-keyword?
                             :tuple (s/tuple qualified-keyword? ::spectomic/datascript-optional-field-schema))))
        :ret ::spectomic/datascript-schema)

(defmacro datascript-schema
  [& specs]
  (datascript-schema* specs))