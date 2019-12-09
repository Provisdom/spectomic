# Change Log

## [0.7.11] - 2019-12-09
### Added
- Added `datascript-schema-from-datomic-schema`

## [0.7.10] - 2019-12-09
### Changed
- Switched from Boot to deps.edn
- Switching from Travis to CircleCI

## [0.7.9] - 2019-01-10
### Added
- Improve error handling when an exception occurs while sampling.
- Allow `:gen-resize` to be passed as an option to `datomic-schema`.

## [0.7.8] - 2019-01-04
### Fixed
- If :db/valueType is explicitly passed in the extra schema attributes, we should 
  force that value type and not throw any value type related exceptions. [#16](https://github.com/Provisdom/spectomic/pull/16)

## [0.7.7] - 2018-06-14
### Added
- Handle `s/and` in the form parser.

## [0.7.6] - 2018-04-09
### Added
- Call `spec->datomic-schema` within the `(clojure.spec.alpha/coll-of 
  clojure.spec.alpha/every)` case in `find-type-via-form` to further increase
  performance.

## [0.7.5] - 2018-03-21
### Added
- Call `spec->datomic-schema` within `find-type-via-form` to further increase
  performance.

## [0.7.4] - 2018-03-21
### Added
- Decrease schema generation time for some collections and maps by setting the 
  type based on the Spec form.

## [0.7.3] - 2018-02-12
### Fixed
- Include `:db/isComponent` in DataScript schema.

## [0.7.2] - 2017-11-01
### Fixed
- The `datascript-schema` function was incorrectly dropping the `:db/identity` values from maps.

## [0.7.1] - 2017-10-31
### Fixed
- Compatibility with Clojure 1.9.0-beta4 by using `decimal?` instead of `bigdec?`.
### Changed
- Updated to `[org.clojure/spec.alpha "0.1.143"]`.

## [0.7.0] - 2017-10-31
### Breaking
- Removed `:db/valueType` from DataScript schema except when it is `:db.type/ref`.
### Added
- Ensure generated schema can be transacted into Datomic and DataScript.
- Datomic free and DataScript as test level dependencies.

## [0.6.1] - 2017-10-30
### Fixed
- Leftover reader conditional causing tests to fail.
- Move org.clojure/spec.alpha back to 0.1.123.

## [0.6.0] - 2017-10-30
### Breaking
- Made `core.cljc` and `specs.cljc` `.clj` files instead of `.cljc`. 

## [0.5.0] - 2017-08-30
### Breaking
- Removed `datomic-schema` and `datascript-schema` macros.
- Changed `datomic-schema*` and `datascript-schema*` to `datomic-schema` and `datascript-schema`,
respectively.

## [0.4.1] - 2017-08-08
### Fixed
- The specs for `datomic-schema*` and `datascript-schema*` are now correct for the two arity case.

## [0.4.0] - 2017-08-08
### Breaking
- API now expects specs to be passed in a vector
### Added
- Options map for API that can be passed a function to resolve custom types (resolves [#5](https://github.com/Provisdom/spectomic/issues/5))

## [0.3.1] - 2017-08-08
### Added
- Spec for schema entries [#6](https://github.com/Provisdom/spectomic/pull/6)
- Explicit error when schema entry does not conform to spec. [#6](https://github.com/Provisdom/spectomic/pull/6)
### Changed
- Internal code structure
