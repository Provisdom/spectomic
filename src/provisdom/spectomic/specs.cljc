(ns provisdom.spectomic.specs
  (:require
    [clojure.spec :as s]
    [clojure.spec.gen :as gen]
    [clojure.string :as str]))


(def datomic-value-types
  #{:db.type/string :db.type/boolean :db.type/long :db.type/bigint :db.type/float :db.type/double :db.type/bigdec
    :db.type/instant :db.type/uuid :db.type/uri :db.type/keyword :db.type/bytes :db.type/ref})

(def datomic-schema-keys
  #{:db/id :db/ident :db/valueType :db/cardinality :db/doc :db/unique :db/index :db/isComponent :db/noHistory
    :db/fulltext :db.install/_attribute :db.install/_partition})

(s/def ::tempid
  (s/with-gen
    (s/or
      #?@(:clj [:dbid #(instance? datomic.db.DbId %)])
      :string (s/and string? #(not (str/starts-with? % ":"))))
    (fn [] (gen/return (datomic.api/tempid :db.part/db)))))

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