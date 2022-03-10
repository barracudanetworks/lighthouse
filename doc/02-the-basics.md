# The Basics

This section introduces the basic building blocks of `lighthouse` and how they interact.

## Library Files

At its core, `lighthouse` simply defines and manages a hierarchy of library files. Library files are EDN files on disk and contain a single top-level map. A simple example of a library file would be:

```clojure
;; foo.edn
{:foo "bar"}
```
## Metadata

The example above isn't really exciting. Where the excitement comes in is when you introduce metadata into your library files. Metadata defines:

- how the relationships between library files are defined
- how your library file may render different depending on the context

The implementation of metadata is [explained later](/doc/05-deep-dive.md#Metadata), but for now it's enough to know that:

- metadata is defined by providing a symbol (or keyword) namespaced with `"def"`
- metadata is only defined at the top-level of the map
- you can reference metadata by using a symbol (or keyword) namespaced with `"ref"`

Here's an example of metadata:

```clojure
;; bar.edn
{def/from ["foo"] ;; 1
 def/some-meta 42 ;; 2
 :meaning-of-life ref/some-meta} ;; 3
```

The library file above shows the main uses of metadata:

1. `def/from` defines connections between library files. `def/from ["foo"]` means that `bar.edn` inherits from the `foo.edn` library file. [More on this later.](/doc/05-deep-dive.md#Inheritance)
2. `def/some-meta` defines specific metadata that can be referenced in this library file.
3. Speaking of, `ref/some-meta` will be replaced with the `some-meta` metadata during processing

## Manifest Files

Manifest files are a super-set of library files. They have the same inheritance semantics as library files and provide a way to group library files into 1-to-many files.
This means that a single manifest file can result in multiple files on disk when processing!

To achieve this, manifest files are processed recursively and turned into a collection of output files and their associated library files. Output files are extracted from manifest files when:

1. the current key has the `group` namespace
2. processing hits a library file

When one of the above is hit, the path down from that point  is tracked and used as the basename for the resulting output file.

Example:

```clojure
;; stack.edn
{:my-app
 {:deployment {def/from ["app-deployment"]}
  :service {def/from ["app-service"]}}

 group/other-app
 {:deployment {def/from ["other-deployment"]}
  :service {def/from ["other-service"]}}}
```

This manifest file will render 3 files: `my-app.deployment.yaml`, `my-app.service.yaml`, `other-app.yaml`.

[<: Overview](/doc/01-overview.md) | [Configuration and the CLI :>](/doc/03-configuration-and-the-cli.md)
