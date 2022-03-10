# Lighthouse

Generate Kubernetes manifests by combining EDN data instead of templating YAML.

## Background

There are [several issues](/doc/01-overview.md#rationale) with the existing
solutions for generating Kubernetes manifests. Helm has idiosyncrasies and YAML
is difficult to template.

Lighthouse takes the approach of using EDN data structures in a directory tree
with simple inheritance rules to support generating manifests in a flexible and
reliable manner.

* Read the [tutorial](doc/tutorial/01-getting-started.md) to get started.
* Read the [manual](/doc/01-overview.md) for more information.

# Installation

## Static binary

Download the latest binary from [Releases](https://github.com/barracudanetworks/lighthouse/releases), and put the `lh` binary in your path.

# License

Copyright 2022 Barracuda Networks, Inc.

[3-clause BSD license](LICENSE.md)
