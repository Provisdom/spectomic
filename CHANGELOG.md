# Change Log

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
