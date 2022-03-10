# Processors

This section describes the what and why of `processors`.

## Intro

During the merging and building process of library files, `lighthouse` provides a couple hooks for any additional processing:

- one allows for custom metadata resoltion
- another allows for arbitrary data manipulation

`processors` implement at least one of these hooks to provide context-specifc behavior.

## Current Processors

### `:kube`

This processor provides `kubernetes`-focused manipulation of `lighthouse` data:

#### image tags
This processor provides custom resolution for all `ref/tags.*` metadata references. The metadata keys used for this check
are configurable.

```clojure
;; given metadata
;; {:tags {:my-app "v1.0.1-beta"}
;;  :registry "my-registry"}
{:image ref/tags.my-app} ;; -> {:image "my-registry/my-app:v1.0.1-beta"}
```

#### vector of named maps
Several keys in `kubernetes` parlance require a vector of maps with each map containing a `name` key.
In order to simplify these keys and provide simpler merge logic, this processor expects the aformentioned keys to be maps instead of vectors of maps.
The keys in the maps will be assoc'd into their values under the `:name` key.

The keys that receive this transformation are:
 - `:ports`
 - `:volumes`
 - `:volume-mounts`
 - `:containers`
 - `:init-containers`

#### environment
This processor provides some small envronment-specific manipulations:

- keywords with namespaces starting with `:config` will be replaces with `configMapKeyRef`s
- keywords with namespaces starting with `:secret` will be replaces with `secretKeyRef`s
- keywords with namespaces starting with `:field` will be replaces with `fieldRef`s

This processor also provides one _large_ enviroment-specific manipulation: akin to the above `vector of named maps` transform, the `:env` key is required to be an arbitrarily-nested map.
The processor takes this map and translates it into a flat vector of env vars compatible with libraries like [`edn-env`][1] or [`cprop`][2].

Having `:env` simply be a map means that configuration via `:env` (in most cases) maps directly onto the config packaged with your application. In almost all situations,
you can simply copy and paste your application config into the `:env` key, change what values you care about, and your application will be properly configured!

[<: Configuration and the CLI](/doc/03-configuration-and-the-cli.md) | [Deep Dive :>](/doc/05-deep-dive.md)

[1]: https://github.com/DarinDouglass/edn-env
[2]: https://github.com/tolitius/cprop
