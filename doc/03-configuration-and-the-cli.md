# Configuration and the CLI

This section covers how to configure `lighthouse`.

## CLI Summary

The main way to interact with `lighthouse` is via its CLI tool `lh`. This tool provides ways to:

- visualize library or manifest files
- build/render library or manifest files
- update manifests in-place

For each file `lh` operates on, it will search up the directory tree for a `lighthouse.edn` file.
These `lighthouse.edn` files are used to configure `lighthouse`. The defaults for this file can be found [here](/src/lh/config.clj).

### Configuration Reference

| Setting             | Description                                                                     |
| ------------------- | ------------------------------------------------------------------------------- |
| `:library-prefix`   | used to denote library files                                                    |
| `:metadata-prefix`  | used to denote metadata files                                                   |
| `:namespaces`       | defines all namespaces `lighthouse` uses                                        |
| `:output-prefix`    | used to define where renderings get written to disk                             |
| `:output-format`    | defines what serialization format the resulting manfiests will be serialized in |
| `:processor`        | defines what (if any) processing should happen on the data                      |
| `:processors`       | a map of config used to configure `:processor`                                  |

[<: The Basics](/doc/02-the-basics.md) | [Post-Processing :>](/doc/04-processors.md)
