# Parent Libraries

In the previous section, the `nginx.edn` file is called a library. Parent
libraries are common data that will be merged with the current library's data.

Parent libraries are EDN files that are placed in a `lib` directory somewhere
up the directory tree from the files being built.

When a parent library is included, it's contents are deep merged in order with
the current library data going last. Parent libraries can include other
libraries and so create a hierarchy of data that can be overridden and
specialized at each level.

In the last section we created a single pod. Now, we'll create another pod and
see how we can use parent libraries to share common structure.

## Deep merging

When Lighthouse merges data it does it deeply. Deep merging takes two maps of
nested data and recursively merges the second into the first.

A simple example to start. Take these two maps of data:

```clojure
{:simple-value 1
 :map {:one :value}
 :vector [1 2 3]}

{:simple-value 3
 :map {:two :other-value}
 :vector [4 5 6]}
```

If you deep merge these two, this is the result:

```clojure
{:simple-value 3
 :map {:one :value
       :two :other-value}
 :vector [1 2 3 4 5 6]}
```

As you can see `:simple-value` is overridden, the `:map` key has the result of
merging the two value maps, and `:vector` is a concatenation of the value
vectors.

When the data is deeply nested, deep merge finds those values and merges them.
Given these two:

```clojure
{:deep
 {:one
  {:two
   {:three :value}}}}

{:deep
 {:one
  {:two
   {:three :other-value
    :four :more-data}}}}
```

The result is:

```clojure
{:deep
 {:one
  {:two
   {:three :other-value
    :four :more-data}}}}
```

There are more nuances to the way Lighthouse deep merges, including the ability
to arbitrarily replace data instead of merging, but the above is sufficient for
now.

## Adding a parent library for pods

In the last section, there were two files (`lighthouse.edn` and `nginx.edn`).
Now we'll extract a parent library for the common pod data.

Create a `lib/` directory and put the following into a file called `lib/pod.edn`:

```clojure
{:api-version "v1"
 :kind "Pod"}
```

Then update `nginx.edn` to look like this:

```clojure
{def/from ["pod"]
 :metadata {:name "nginx"}
 :spec {:containers
        {"nginx"
         {:image "nginx:1.14.2"
          :ports {:http {:container-port 80}}}}}}
```


If you re-run the `lh build nginx.edn` command again, the manifest file
`manifests/nginx.yaml` will still be the same:

```yaml
# ------------------------------------------------------------
# AUTO-GENERATED FILE. ANY MANUAL CHANGES WILL BE OVERWRITTEN.
# ------------------------------------------------------------
---
apiVersion: v1
kind: Pod
metadata:
  name: nginx
spec:
  containers:
  - image: nginx:1.14.2
    name: nginx
    ports:
    - containerPort: 80
      name: http
```

The power of parent libraries comes from their reuse. So, let's make another
pod that leverages the same parent.

Create another library called `echo.edn` with the following contents:

```clojure
{def/from ["pod"]
 :metadata {:name "echo"}
 :spec {:containers
        {"echo"
         {:image "hashicorp/http-echo:0.2.3"
          :ports {:http {:container-port 5678}}}}}}
```

Building this (with `lh build echo.edn`) results in
a `manifests/echo.yaml` that looks like this:

```yaml
# ------------------------------------------------------------
# AUTO-GENERATED FILE. ANY MANUAL CHANGES WILL BE OVERWRITTEN.
# ------------------------------------------------------------
---
apiVersion: v1
kind: Pod
metadata:
  name: echo
spec:
  containers:
  - image: hashicorp/http-echo:0.2.3
    name: echo
    ports:
    - containerPort: 5678
      name: http
```

There is still some structural duplication in `nginx.edn` and `echo.edn`. To
refactor this out, we can leverage metadata.

## Metadata

Metadata is arbitrary out-of band data that can be used to populate the data
that Lighthouse merges.

For example, we can change the `nginx.edn` to look like this:

```clojure
{def/from ["pod"]
 def/name "nginx"
 :metadata {:name ref/name}
 :spec {:containers
        {ref/name
         {:image "nginx:1.14.2"
          :ports {:http {:container-port 80}}}}}}
```

The `def/name` line creates a new entry in the metadata map, and then the two
places that the string "nginx" were have been replaced with `ref/name`, which
looks up the value.

Metadata can be used in parent library files as well. This is because all
metadata for a given library and its parents is merged first and then each
library (and itself) are processed before merging.

So, if we change `lib/pod.edn` to contain this:

```clojure
{:api-version "v1"
 :kind "Pod"
 :metadata {:name ref/name}}
```

We can remove the `:metadata` key from each of `nginx.edn` and `echo.edn`:

```clojure
;; nginx.edn
{def/from ["pod"]
 def/name "nginx"
 :spec {:containers
        {ref/name
         {:image "nginx:1.14.2"
          :ports {:http {:container-port 80}}}}}}

;; echo.edn
{def/from ["pod"]
 def/name "echo"
 :spec {:containers
        {ref/name
         {:image "hashicorp/http-echo:0.2.3"
          :ports {:http {:container-port 5678}}}}}}
```

And the output is identical to before.

This shows that structure can be extracted into libraries and parameterized
with metadata.

[<: Getting Started](/doc/tutorial/01-getting-started.md) | [Directory Structure :>](/doc/tutorial/03-directory-structure.md)
