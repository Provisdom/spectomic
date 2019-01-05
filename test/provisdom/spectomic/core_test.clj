(ns provisdom.spectomic.core-test
  (:require
    [clojure.test :refer :all]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.spec.test.alpha :as st]
    [datomic.api :as d]
    [datascript.core :as ds]
    [provisdom.spectomic.core :as spectomic]))

(st/instrument)

;; basic Datomic types
(s/def ::string string?)
(s/def ::int int?)
(s/def ::inst inst?)
(s/def ::double double?)
;; the default generator for float? will generate Doubles instead of Floats so we need our own
;; generator and predicate function.
(s/def ::float
  (s/with-gen
    #(instance? java.lang.Float %)
    #(gen/fmap float
               (s/gen (s/double-in :min Float/MIN_VALUE :max Float/MAX_VALUE :infinite? false :NaN? false)))))
(s/def ::uuid uuid?)
(s/def ::bigdec decimal?)
(s/def ::bigint (s/with-gen
                  #(instance? java.math.BigInteger %)
                  (fn []
                    (gen/fmap #(BigInteger/valueOf %)
                              (s/gen int?)))))
(s/def ::uri uri?)
(s/def ::keyword keyword?)
(s/def ::bytes bytes?)

(s/def ::nilable-int (s/nilable int?))

(s/def ::int-coll (s/coll-of ::int))
(s/def ::nilable-int-coll (s/coll-of ::nilable-int))

(s/def ::map (s/keys :req [::int ::string]))
(s/def ::map-and (s/and map? ::map))
(s/def ::map-and-keys (s/and map? (s/keys :req [::int])))
(s/def ::map-and-two-types (s/and (s/keys :req [::int]) (s/coll-of int?)))
(s/def ::map-coll-of-and (s/and ::nilable-map-coll sequential?))
(s/def ::map2 (s/keys :req [::keyword]))
(s/def ::merged-map (s/merge ::map ::map2))
(s/def ::nilable-map (s/nilable ::map))
(s/def ::nilable-map-verbose (s/nilable (s/keys :req [::int ::string])))
(s/def ::nilable-map-coll (s/coll-of ::nilable-map))
(s/def ::nilable-map-coll-verbose (s/coll-of (s/nilable (s/keys :req [::int]))))

;; these specs will fail schema generation
(s/def ::nil nil?)
(s/def ::or (s/or :string string? :num int?))
(s/def ::or-coll (s/coll-of ::or))
(s/def ::coll-of-coll (s/coll-of (s/coll-of string?)))
(defrecord MyObject [])
(s/def ::myobject (s/with-gen #(instance? MyObject %)
                              #(gen/return (MyObject.))))

(deftest find-type-via-form-test
  (let [ref-one {:db/valueType   :db.type/ref
                 :db/cardinality :db.cardinality/one}
        ref-many {:db/valueType   :db.type/ref
                  :db/cardinality :db.cardinality/many}]
    (is (= ref-one (spectomic/find-type-via-form ::map)))
    (is (= ref-one (spectomic/find-type-via-form ::merged-map)))
    (is (= ref-one (spectomic/find-type-via-form ::nilable-map)))
    (is (= ref-one (spectomic/find-type-via-form ::nilable-map-verbose)))
    (is (= ref-many (spectomic/find-type-via-form ::nilable-map-coll)))
    (is (= ref-many (spectomic/find-type-via-form ::nilable-map-coll-verbose)))
    (is (= ref-one (spectomic/find-type-via-form `(s/and coll? ::map map?))))
    (is (= ref-one (spectomic/find-type-via-form ::map-and)))
    (is (= ref-one (spectomic/find-type-via-form ::map-and-keys)))
    (is (= ref-many (spectomic/find-type-via-form ::map-coll-of-and)))
    (is (nil? (spectomic/find-type-via-form ::keyword)))
    (is (nil? (spectomic/find-type-via-form ::coll-of-coll)))
    (is (nil? (spectomic/find-type-via-form (fn []))))
    (is (nil? (spectomic/find-type-via-form ::map-and-two-types)))))

(deftest find-type-via-generation-test
  (is (= {:db/valueType   :db.type/ref
          :db/cardinality :db.cardinality/one}
         (spectomic/find-type-via-generation ::map nil))))

(deftest datomic-schema-test
  (testing "basic datomic types"
    (is (= [{:db/ident       ::string
            :db/valueType   :db.type/string
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::int
            :db/valueType   :db.type/long
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::inst
            :db/valueType   :db.type/instant
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::double
            :db/valueType   :db.type/double
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::float
            :db/valueType   :db.type/float
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::uuid
            :db/valueType   :db.type/uuid
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::bigdec
            :db/valueType   :db.type/bigdec
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::bigint
            :db/valueType   :db.type/bigint
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::uri
            :db/valueType   :db.type/uri
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::keyword
            :db/valueType   :db.type/keyword
            :db/cardinality :db.cardinality/one}
           {:db/ident       ::bytes
            :db/valueType   :db.type/bytes
            :db/cardinality :db.cardinality/one}]
          (spectomic/datomic-schema [::string ::int ::inst ::double ::float ::uuid
                                     ::bigdec ::bigint ::uri ::keyword ::bytes]))))
  (testing ":db.type/ref"
    (is (= [{:db/ident       ::map
             :db/valueType   :db.type/ref
             :db/cardinality :db.cardinality/one}
            {:db/ident       ::nilable-map
             :db/valueType   :db.type/ref
             :db/cardinality :db.cardinality/one}]
           (spectomic/datomic-schema [::map ::nilable-map]))))

  (testing ":db.cardinality/many"
    (is (= [{:db/ident       ::int-coll
             :db/valueType   :db.type/long
             :db/cardinality :db.cardinality/many}
            {:db/ident       ::nilable-int-coll
             :db/valueType   :db.type/long
             :db/cardinality :db.cardinality/many}]
           (spectomic/datomic-schema [::int-coll ::nilable-int-coll]))))

  (testing "extra schema attrs"
    (is (= [{:db/ident       ::int
             :db/valueType   :db.type/long
             :db/cardinality :db.cardinality/one
             :db/index       true
             :db/unique      :db.unique/identity}]
           (spectomic/datomic-schema [[::int {:db/index true :db/unique :db.unique/identity}]]))))

  (testing "extra schema attrs still generates spec if only valueType is specified."
    (is (= [{:db/ident       ::int
             :db/valueType   :db.type/long
             :db/cardinality :db.cardinality/one}]
           (spectomic/datomic-schema [[::int {:db/valueType :db.type/long}]]))))

  (testing "extra schema attrs skips generation if both valueType and cardinality are passed."
    (is (= [{:db/ident       ::int
             :db/valueType   :db.type/long
             :db/cardinality :db.cardinality/one}]
           (spectomic/datomic-schema [[::int {:db/valueType   :db.type/long
                                              :db/cardinality :db.cardinality/one}]]))))

  (are [spec] (thrown? clojure.lang.ExceptionInfo (spectomic/datomic-schema [spec]))
    ::nil ::or ::or-coll ::coll-of-coll ::myobject))

(deftest datascript-schema-test
  (are [schema specs] (= schema (spectomic/datascript-schema specs))
    {::double   {:db/cardinality :db.cardinality/one}
     ::inst     {:db/cardinality :db.cardinality/one}
     ::int      {:db/cardinality :db.cardinality/one}
     ::bigint   {:db/cardinality :db.cardinality/one}
     ::float    {:db/cardinality :db.cardinality/one}
     ::string   {:db/cardinality :db.cardinality/one}
     ::keyword  {:db/cardinality :db.cardinality/one}
     ::bigdec   {:db/cardinality :db.cardinality/one}
     ::bytes    {:db/cardinality :db.cardinality/one}
     ::uri      {:db/cardinality :db.cardinality/one}
     ::uuid     {:db/cardinality :db.cardinality/one}
     ::int-coll {:db/cardinality :db.cardinality/many}
     ::map      {:db/cardinality :db.cardinality/one
                 :db/valueType   :db.type/ref}}
    [::string ::int ::inst ::double ::float ::uuid
     ::bigdec ::bigint ::uri ::keyword ::bytes ::int-coll
     ::map]
    {::int {:db/cardinality :db.cardinality/one
            :db/unique      :db.unique/identity
            :db/isComponent true}}
    [[::int {:db/index       true
             :db/unique      :db.unique/identity
             :db/isComponent true}]]))

(deftest datomic-schema-valueType-test
  (is (= [{:db/ident       ::string
           :db/valueType   :db.type/keyword
           :db/cardinality :db.cardinality/one}]
         (spectomic/datomic-schema
           [[::string {:db/valueType :db.type/keyword :db/cardinality :db.cardinality/one}]]))))

(def test-schema-specs
  [::string ::int ::inst ::double ::float ::uuid
   ::bigdec ::bigint ::uri ::keyword ::bytes
   ::map ::nilable-map
   ::int-coll ::nilable-int-coll
   [::int {:db/index true :db/unique :db.unique/identity}]])

(deftest datomic-schema-transaction-test
  (let [db-uri "datomic:mem://test"
        _ (d/create-database db-uri)
        conn (d/connect db-uri)
        schema (spectomic/datomic-schema test-schema-specs)]
    (testing "able to transact Spectomic generated schema to Datomic"
      (is @(d/transact conn schema)))))

(deftest datascript-schema-transaction-test
  (let [schema (spectomic/datascript-schema test-schema-specs)]
    (testing "able to transact Spectomic generated schema to DataScript"
      (is (ds/create-conn schema)))))