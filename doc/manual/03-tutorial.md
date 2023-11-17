# Tutorial

## Getting Started

Lighthouse is a tool for merging data together and exporting that data to disk.
This can sound a bit abstract, but it's quite useful when you see a few
examples.

### Extensible Data Notation

Lighthouse's data is in the Extensible Data Notation (EDN) format, which is
a subset of the Clojure syntax. It is to Clojure what JSON is to JavaScript.

One useful benefit over JSON is that comments are supported.

For a quick primer into the format, check out [this guide](https://learnxinyminutes.com/docs/edn/).

### First manifest

The most common task to automate with Lighthouse is generating Kubernetes
manifests, so we'll start with a simple pod.

First, make a directory for your Lighthouse data and change into it:

```
mkdir tutorial
cd tutorial
```

#### Files

Only two files are needed to generate the Kubernetes yaml for a pod.

The first is the Lighthouse config itself. This file anchors the base of the
Lighthouse data and is used as a reference point when searching for parent
libraries to use.

##### File 1: Lighthouse config file for Kubernetes

Create the file `lighthouse.edn`:

```clojure
{:output-format :yaml}
```

This sets the output format to be YAML (as opposed to JSON or EDN).

##### File 2: Library file for the pod itself

Create the file `nginx.edn`:

```clojure
{:api-version "v1"
 :kind "Pod"
 :metadata {:name "nginx"}
 :spec {:containers
        {"nginx"
         {:image "nginx:1.14.2"
          :ports {:http {:container-port 80}}}}}}
```

#### Run Lighthouse

Run the Lighthouse build subcommand to generate the manifest:

```
lh build nginx.edn
```

The resulting manifest will be in the `manifests/nginx.yaml`:

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
    nginx:
      image: nginx:1.14.2
      ports:
        http:
          containerPort: 80
```

You will notice that the above is not quite valid Kubernetes YAML. The
containers and ports are both maps. The reason for this is that Lighthouse
operates on nested maps, for reasons that will become evident later. For
now, we need to convert those maps into collections, and to do that we need to
enable the "kube" processor, which knows how to do the right conversion. Enable
this processor by adding `:processor :kube` to the `lighthouse.edn` config
file. So now it should look like this:

```clojure
{:output-format :yaml
 :processor :kube}
```

Now, if you re-run the `lh build nginx.edn` command from above, the
resulting `manifests/nginx.yaml` is now:

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

That's more like it. Now we can take that manifest and submit it to Kubernetes.

```
$ kubectl get pod
No resources found in default namespace.

$ kubectl apply -f manifests/nginx.yaml
pod/nginx created

$ kubectl get pod
NAME    READY   STATUS              RESTARTS   AGE
nginx   0/1     ContainerCreating   0          5s

$ kubectl get pod
NAME    READY   STATUS    RESTARTS   AGE
nginx   1/1     Running   0          28s

$ kubectl delete -f manifests/nginx.yaml
pod "nginx" deleted
```

## Parent Libraries

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

### Deep merging

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

### Adding a parent library for pods

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

### Metadata

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


## Directory Structure

Up until this point, we have been dealing with a very simple directory
structure. In the wild, Lighthouse trees usually have more depth, which grants
more flexibility when constructing data for different environments.

For instance, take this directory tree:

```
deploy/
├── lighthouse.edn
├── lib
│   ├── nginx.edn
│   └── pod.edn
├── prod
│   └── nginx.edn
├── qa
│   └── nginx.edn
└── staging
    └── nginx.edn
```

As before, the `lighthouse.edn` file anchors the root of the data files.

There are several `nginx.edn` files. The one in the `lib` directory looks like this:

```clojure
{def/from ["pod"]
 def/name "nginx"
 :spec {:containers
        {ref/name
         {:image "nginx:1.14.2"
          :ports {:http {:container-port 80}}}}}}
```

And the `nginx.edn` file in each environment directory (`prod`, `qa`,
`staging`) looks like this:

```clojure
{def/from ["nginx"]}
```

Because there is usually so much sharing between environments, the "leaf"
libraries tend to be very short and only contain metadata overrides.

### Multiple lib directories

Lighthouse looks for libraries at each level up the directory tree until it
reaches the `lighthouse.edn` file.

For instance, if we want the `nginx` pod to be in a different Kubernetes
namespace in prod, we can accomplish this by adding a `lib` directory inside
`prod` with a library with the same name as the base library (`nginx.edn`):

```
├── lib
│   ├── nginx.edn
│   └── pod.edn
├── prod
│   ├── lib
│   │   └── nginx.edn
│   └── nginx.edn
```

And if that `prod/lib/nginx.edn` file looks like this:

```
{:metadata {:namespace "prod-ns"}}
```

When the prod nginx is built (with `lh build prod/nginx.edn`), the
resulting manifest is:

```yaml
# ------------------------------------------------------------
# AUTO-GENERATED FILE. ANY MANUAL CHANGES WILL BE OVERWRITTEN.
# ------------------------------------------------------------
---
apiVersion: v1
kind: Pod
metadata:
  name: nginx
  namespace: prod-ns   <-- new line added because of prod nginx library
spec:
  containers:
  - image: nginx:1.14.2
    name: nginx
    ports:
    - containerPort: 80
      name: http
```

Lighthouse accomplished this by merging the data in this order:

* `lib/pod.edn`
* `lib/nginx.edn`
* `prod/lib/nginx.edn`
* `prod/nginx.edn`

What if we wanted the namespace to be `prod-ns` for all pods in the `prod`
environment? Well, then we could rename `prod/lib/nginx.edn` to
`prod/lib/pod.edn`, and anything that specifies a parent library of `pod`
(directly or transitively) will see that data merged in.

In this case, the merging is in this order:

* `lib/pod.edn`
* `prod/lib/pod.edn`
* `lib/nginx.edn`
* `prod/nginx.edn`

This highlights the fact that libraries of the same name at different levels
are merged together, before they are merged into their child libraries.


## Next steps

Lighthouse provides more facilities for tuning the output, such as:

* Running snippets of code to calculate values
* Combining several library files into a single output manifest
* Storing metadata in separate files

[<: Installation](02-installation.md) | [Configuration and CLI :>](04-config-and-cli.md)
