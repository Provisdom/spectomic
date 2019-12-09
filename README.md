# spectomic
[![CircleCI](https://circleci.com/gh/Provisdom/spectomic.svg?style=svg)](https://circleci.com/gh/Provisdom/spectomic)

Generate Datomic or Datascript schema from your Clojure(script) specs.

## Installation

[](dependency)
```clojure
[provisdom/spectomic "0.7.11"] ;; latest release
```
[](/dependency)

## Usage

Let's take a look at a basic example.

```clojure
(require '[clojure.spec :as s]
         '[provisdom.spectomic.core :as spectomic])
=> nil

(s/def ::string string?)
=> :boot.user/string

(spectomic/datomic-schema [::string])
=> [{:db/ident       :boot.user/string
     :db/valueType   :db.type/string
     :db/cardinality :db.cardinality/one}]
```

In this example we define the spec `::string` and then pass it to `datomic-schema`
which returns a collection of schema matching the data our `::string` spec 
represents. Now let's look at a more complicated example.

```clojure
(s/def :entity/id uuid?)
=> :entity/id

(s/def :user/name string?)
=> :user/name

(s/def :user/favorite-foods (s/coll-of string?))
=> :user/favorite-foods

(s/def :order/name string?)
=> :order/name

(s/def :user/order (s/keys :req [:entity/id :order/name]))
=> :user/orders

(s/def :user/orders (s/coll-of :user/order))
=> :user/orders

(s/def ::user (s/keys :req [:entity/id :user/name :user/favorite-foods :user/orders]))
=> :boot.user/user

(spectomic/datomic-schema [[:entity/id {:db/unique :db.unique/identity
                                        :db/index  true}]
                           :user/name
                           :user/favorite-foods
                           :user/orders])
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

In this example we have a `::user` entity that is uniquely identified by an 
`:entity/id`. The user also has a collection of his or her favorite foods 
`:user/favorite-foods` and a collection of orders `:user/orders`. 

We need to let `datomic-schema` know that `:entity/id` is unique. We do this be 
providing `datomic-schema` with a tuple instead of just the spec keyword. The 
first element of the tuple is the spec and the second element is any extra schema 
fields to be added to the attribute. In this case we attach `:db/unique` and 
`:db/index`. You can see in the outputted schema that `:entity/id` does indeed 
have those extra fields.

`:user/name` is not particularly interesting as it is similar to our basic example.

`:user/favorite-foods` is represented as a collection of strings in our example. 
And as you can see in the returned schema, `:user/favorite-foods` is 
`:db.cardinality/many` and `:db.type/string`.

`:user/orders` is a collection of maps of the for dictated by the spec `::orders`. 
And our returned schema is of type `:db.type/ref` and `:db.cardinality/many`.

### Resolving Custom Types
Your code may use types that are not resolvable to Datomic types with the default 
type type resolver implementation. One option to this problem is to use the schema 
entry format you saw above with `:entity/id`. If you recall, we set the `:db/unique` 
property for `:entity/id` to `:db.unique/identity`. You can actually manually set 
the `:db/valueType` too. This could, however, become very repetitive. 

The second option is to pass a map with the `:custom-type-resolver` key set to a 
function that returns a Datomic type. If the default type resolver cannot resolve 
an object's type, your function will be called with the object passed to it as 
the only argument. Your function is expected to return a valid Datomic type, as 
defined [here](http://docs.datomic.com/schema.html#required-schema-attributes).

## Usage in Production

This library provides two functions for generating schema from specs: `datomic-schema` 
and `datascript-schema`. Both function calls occur at **runtime**. This means 
that if you include this library as a runtime dependency, test.check will also be 
included. Often you don't want test.check as a runtime dependency so it is suggested 
that you include this dependency as `test` scope 
(e.g. `[provisdom/spectomic "x.x.x" :scope "test"]`). Including this library with 
`test` scope means that all your calls need to happen at compile time or elsewhere. 
Here are two examples of how this library can be used at compile time.

### Macro

It is convenient to store the list of specs you want to convert into schema as a 
var. By doing so you are able to easily add new specs to the schema list at any 
time. We will use a `def`'ed var as an example. 

```clojure
(def schema-specs [[:entity/id {:db/unique :db.unique/identity :db/index true}] :user/name :user/favorite-foods])
```

Now let's write a `def`-like macro that will generate our schema at compile time:

```clojure
(defmacro defschema
  [name]
  `(def ~name (spectomic/datomic-schema schema-specs)))

(defschema my-schema)
```

Now our schema is statically compiled and available in the var `my-schema`.

### Build time

Sometimes you may want to save your schema into an actual EDN file for use in 
multiple places. This can be easily accomplished with a Boot task.

```clojure
(require '[provisdom.spectomic.core :as spectomic])

(deftask generate-schema
  [s sym VAL sym "This symbol will be resolved and have its content passed to `datomic-schema`."]
  (spit "my-schema.edn" (spectomic/datomic-schema @(resolve sym))))
```

This task is simple but does not adhere to Boot's design patterns. Here's a 
slightly more complex example that integrates well with other tasks.

```clojure
(require '[clojure.java.io :as io])

(deftask generate-schema
  [s sym VAL sym "This symbol will be resolved and have its content passed to `datomic-schema`."
   o out-file VAL str "Name of the file for the schema to be outputted to. Defaults to schema.edn"]
  (let [specs-var (resolve sym)]
    (assert specs-var "The symbol provided cannot be resolved.")
    (with-pre-wrap fileset
      (let [out-file-name (or out-file "schema.edn")
            out-dir (tmp-dir!)
            out-file (io/file out-dir out-file-name)]
        (spit out-file (spectomic/datomic-schema @specs-var))
        (commit! (add-resource fileset out-dir))))))
```

## Caveats

### Misleading Generators

When writing your specs you need to be mindful of the generator that is used for 
the spec. One misleading predicate generator is `float?`. `float?`'s generator 
actually uses the same generator as `double?`, meaning it does not return a number 
with type `java.lang.Float`. This is problematic for our schema generator as it 
relies on your objects having the correct type that they represent. This is not 
a bug in Clojure spec however. If you look at how `float?` is defined you will 
see that `float?` returns true if the object is a `java.lang.Double` or a 
`java.lang.Float`. To combat this we can write our own `::float` spec like this:

```clojure
(s/def ::float
  (s/with-gen
    #(instance? java.lang.Float %)
    #(gen/fmap float
               (s/gen (s/double-in :min Float/MIN_VALUE :max Float/MAX_VALUE :infinite? false :NaN? false)))))
```

### Rare Edge Cases

If you write a Spec that uses a generator that will return values of a different 
type very rarely then you will run into schema generation problems. Make sure 
your generators are returning consistent types.

## Implementation

Rather than parsing every Spec form, we chose to implement this library using 
generators. Every Spec that is passed to `datomic-schema` or `datascript-schema` 
is sampled 100 times using test.check. The type of each sample is then matched 
with a Datomic type. Next we make sure that each sample is of the same type. If 
your generator is returning multiple types for a Spec then it's not clear how the 
schema should be generated so an error is thrown. If the type is a collection 
then we need to verify that for each sample, every element in the collection is 
of the same type. If that is true then we return a map with `:db.cardinality/many`
and the `:db/valueType` set to the type of object the collection contains. 

Because generating samples for specs can end up taking a significant amount of 
time, we do some Spec form parsing up front to try and determine the Datomic
type. If the Spec form is not handled then we fall back on generation.

## License

Copyright © 2017 Provisdom

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
