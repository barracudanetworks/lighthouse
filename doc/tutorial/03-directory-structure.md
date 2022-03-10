# Directory Structure

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

## Multiple lib directories

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

[<: Parent Libraries](/doc/tutorial/02-libraries.md) | [Next Steps :>](/doc/tutorial/04-next.md)
