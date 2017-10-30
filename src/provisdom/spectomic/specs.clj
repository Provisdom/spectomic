(ns provisdom.spectomic.specs
  (:require
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]
    [clojure.string :as str]
    [clojure.spec.alpha :as s]))


(def datomic-value-types
  #{:db.type/string :db.type/boolean :db.type/long :db.type/bigint :db.type/float :db.type/double :db.type/bigdec
    :db.type/instant :db.type/uuid :db.type/uri :db.type/keyword :db.type/bytes :db.type/ref})

(s/def ::tempid
  (s/with-gen
    (s/or
      :dbid #(instance? (Class/forName "datomic.db.DbId") %)
      :string (s/and string? #(not (str/starts-with? % ":"))))
    (fn [] (gen/return ((resolve 'datomic.api/tempid) :db.part/db)))))

(s/def :db/id
  (s/or
    :entity-id number?
    :lookup-ref (s/tuple keyword? (s/or :string string?
                                        :keyword keyword?
                                        :num number?
                                        :uuid uuid?))
    :tempid ::tempid
    :ident keyword?))

(s/def :db/ident keyword?)
(s/def :db/valueType datomic-value-types)
(s/def :db/cardinality #{:db.cardinality/one :db.cardinality/many})
(s/def :db/doc string?)
(s/def :db/unique #{:db.unique/value :db.unique/identity})
(s/def :db/index boolean?)
(s/def :db/isComponent boolean?)
(s/def :db/noHistory boolean?)
(s/def :db/fulltext boolean?)

(s/def ::datascript-optional-field-schema (s/keys :opt [:db/doc :db/unique :db/index :db/isComponent]))
(s/def ::datascript-schema
  (s/map-of keyword? ::datascript-optional-field-schema))


(s/def ::datomic-optional-field-schema
  (s/merge
    ::datascript-optional-field-schema
    (s/keys :opt [:db/id :db/fulltext :db/noHistory])))

(s/def ::datomic-field-schema
  (s/coll-of
    (s/merge
      ::datomic-optional-field-schema
      (s/keys :req [:db/ident :db/valueType :db/cardinality]))))

(s/def ::schema-entry (s/or :att keyword? :att-and-schema (s/tuple keyword? map?)))

(s/def ::custom-type-resolver (s/fspec :args (s/cat :object any?)))
(s/def ::schema-options (s/keys :opt-un [::custom-type-resolver]))