# Deep Dive

This section covers the finer, more detailed aspects of `lighthouse`.

## Inheritance

The `def/from` metadata key is used to define how all library files relate. This key must be a vector
of strings, these strings are known as "parent libraries". Each of these parent libraries must either a
fully-resolvable library filepath or a library file. During processing `lighthouse` resolves these parent
libraries into 1-to-many maps that will be deep-merged into the child library.

In order to know how to resolve the parent libraries, `lighthouse` uses the directory structure of its root folder.
Every directory from the root directory (the directory that contains the `lighthouse.edn` file) to the directory
containing the child library being worked on will be searched for parent libraries that match those given
in the child library's `def/from` metadata. The notable exception is if the parent library is a resolvable path
on disk (realtive to the root directory). If that's the case then no tree searching will occur.

Example tree structure:
```
my-app/
|-- lighthouse.edn
|-- lib
|   \-- my-app.edn
|-- staging
|   \-- stack.edn
\-- prod
    |-- lib
    |   \-- my-app.edn
    |-- us
    |   |-- lib
    |   |   \-- my-app.edn
    |   \-- stack.edn
    |-- europe
    |   \-- stack.edn
```

Assuming `stack.edn` is a manifest file with content `{:my-app {def/from ["my-app"]}`, `def/from` will resolve to:

| directory   | def/from                                                            |
| ----------- | ------------------------------------------------------------------- |
| staging     | `["lib/my-app.edn"]`                                                |
| prod/us     | `["lib/my-app.edn" "prod/lib/my-app.edn" "prod/us/lib/my-app.edn"]` |
| prod/europe | `["lib/my-app.edn" "prod/lib/my-app.edn"]`                          |

## Merging

Once all parent libraries are fully resolved, they'll be deep merged top-down, left-to-right with the child library
being the last thing merged into the data structure.

Our deep merge logic is fairly simple. Given `parent` and `child` values:

|                   | behavior               |
| ----------------- | ---------------------- |
| `^:replace`       | child replaces         |
| `type-mismatch?`  | child replaces         |
| `map?`            | recursive deep-merge   |
| `vector?`         | concat                 |
| `list?`           | child replaces         |
| `child-nil?`      | parent stays           |
| `any?`            | child replaces         |

## Metadata

### The Different Types

There are currently 4 types of metadata, each providing their own functionality:

| type  | description                                       | example                   |
| ----  | ------------------------------------------------- | ------------------------- |
| `def` | defines metadata                                  | `{def/my-val 1}`          |
| `ref` | is replaced with a metadata value                 | `{:foo ref/my-val}`       |
| `env` | is replaced with an env var                       | `{:home env/HOME}`        |
| `in`  | provides a flat way to arbitrarily nest its value | `{:in/some.nested.key 1}` |

### Metadata Files

Alongside the metadata defined in the library files, `lighthouse` reads metadata from disk.
These files are discovered/handled in the same way that parent libraries are for child libraries:

Metadata files with the same name are merged top-down from the root directory to the child library's
directory. The content of the metadata files will be added to the build metadata under a key that
is generated from the file's basename.

Example:
```
my-app/
|-- lighthouse.edn
|-- meta
|   \-- tags.edn # content: {:my-app "development"}
\-- release-cluster
    |-- meta
    |   \-- tags.edn # content: {:my-app "release"}
    \-- my-app.edn
```
The above directory structure will merge `release-cluster/meta/tags.edn` into `meta/tags.edn` and will
expose the following metadata `{:tags {:my-app "release"}}`.

## Other Processing Notables

There are a few things to mention about processing:

1. Before being written to disk, the data goes through some canonicalization
   depending on the output format the manifests are in. JSON/YAML output
   manifests will be `camelCased`. EDN output manifests are left untouched.
   **NOTE:** any `symbol`s left in the data will SKIP this process and will
   instead have `name` called on them.
2. The final processed data will be sorted before being written to disk. This
   is done to ensure consistency between diffs when committing the processed
   manifests
