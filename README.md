# spectomic

Generate Datomic or Datascript schema from your Clojure(script) specs.

## Installation

[](dependency)
```clojure
[provisdom/spectomic "0.1.0"] ;; latest release
```
[](/dependency)

## Usage

Your schema can be generated at compile time or at runtime. For these examples
we will use macros to generate the schema at compile time. Let's take a look at
a basic example.

```clojure
(require '[clojure.spec :as s]
         '[provisdom.spectomic.core :as spectomic])
=> nil

(s/def ::string string?)
=> :boot.user/string

(spectomic/datomic-schema ::string)
=> [{:db/ident       :boot.user/string
     :db/valueType   :db.type/string
     :db/cardinality :db.cardinality/one}]
```

In this example we define the spec `::string` and then pass it to `datomic-schema` which 
returns us a collection of schema matching the data our `::string` spec represents. Now 
let's look at a more complicated example.

```clojure
(s/def :entity/id uuid?)
=> :entity/id

(s/def :user/name string?)
=> :user/name

(s/def :user/favorite-foods (s/coll-of string?))
=> :user/favorite-foods

(s/def :order/name string?)
=> :order/name

(s/def :user/orders (s/keys :req [:entity/id :order/name]))
=> :user/orders

(s/def :user/orders (s/coll-of ::order))
=> :user/orders

(s/def ::user (s/keys :req [:entity/id :user/name :user/favorite-foods :user/orders]))
=> :boot.user/user

(spectomic/datomic-schema [:entity/id {:db/unique :db.unique/identity
                                       :db/index  true}]
                          :user/name
                          :user/favorite-foods)
=> [{:db/ident       :entity/id
     :db/valueType   :db.type/uuid
     :db/cardinality :db.cardinality/one
     :db/unique      :db.unique/identity
     :db/index       true}
    {:db/ident       :user/name
     :db/valueType   :db.type/string
     :db/cardinality :db.cardinality/one}
    {:db/ident       :user/favorite-foods
     :db/valueType   :db.type/string
     :db/cardinality :db.cardinality/many}
    {:db/ident       :user/orders
     :db/valueType   :db.type/ref
     :db/cardinality :db.cardinality/many}]
```

In this example we have a `::user` entity who is uniquely identified by an `:entity/id`. The
user also have a collection of his or her favorite foods `:user/favorite-foods` and a collection
of orders `:user/orders` (the `::orders` spec is defined elsewhere and is not important for this
example). 

We need to let `datomic-schema` know that `:entity/id` is unique. We do this be providing `datomic-schema`
with a tuple instead of just the spec keyword. The first element of the tuple is the spec and the second
element is any extra schema fields to be added to the attribute. In this case we attach `:db/unique` and
`:db/index`. You can see in the outputted schema that `:entity/id` does indeed have those extra fields.

`:user/name` is not particularly interesting as it is similar to our basic example.

`:user/favorite-foods` is represented as a collection of strings in our example. And as you can see in the 
returned schema, `:user/favorite-foods` is `:db.cardinality/many` and `:db.type/string`.

`:user/orders` is a collection of maps of the for dictated by the spec `::orders`. And our returned schema
is of type `:db.type/ref` and `:db.cardinality/many`.

TODO: Add note about float

## License

Copyright © 2017 Provisdom

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
