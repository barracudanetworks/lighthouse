# Introduction

`lighthouse` is a library that manages suites of EDN files and the connections between them. It provides:

  1. simple inheritance rules
  2. ways to provide metadata at the manifest and directory levels
  3. access to fns via `sci`

`lighthouse` takes these files, builds a tree out of them, merges down into a single map, and renders that map on disk.

# Rationale

During the development of our Kubernetes cluster, our team found issues with Helm/YAML manifests when trying to organize our deployments. Among other things:

- Helm has some really annoying/bad idiosyncrasies (again, among others):
  - You cannot provide an image tag that's completely digits. Helm will refuse to parse this as anything other than a number
  - Helm will always pull down all required charts, even if the versions haven't changed.
  - Helm's inheritance / value structure gets REALLY complicated / obtuse if you have nested charts.
- Templates + YAML is a really, REALLY bad pairing.

  When writing templates you need to worry about whitespace. In other languages (JSON, EDN, etc) this isn't a problem since whitespace is ignored. However, YAML applies syntactic significance to whitespaces. Because of this, when developing Helm templates, extra care has to be taken to ensure that the data is indented the proper amount in the final rendered YAML document. This leads to either `indents` being thrown around everywhere or specifically indented (and thus brittle) template functions.

  All of this compounds and eventually the making sure the indentation levels in your template matches properly takes longer than actually implementing the template change in the first place.

All-in-all, Helm and Go templates had just become a labor to deal with and manage, so we started looking for options. We decided we just wanted to deal with plain manifests. However, YAML (as mentioned above) isn't the best data language so we didn't want to use it if we didn't have to. Eventually, we settled on using EDN structures and a small library to convert EDN into YAML. That "small" library has transformed into this library.

[Installation :>](02-installation.md)
