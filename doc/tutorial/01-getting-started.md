# Getting Started

Lighthouse is a tool for merging data together and exporting that data to disk.
This can sound a bit abstract, but it's quite useful when you see a few
examples.

## Download Lighthouse

First, download the proper binary for your platform from the
[latest release](https://github.com/barracudanetworks/lighthouse/releases).

```
$ wget https://github.com/barracudanetworks/lighthouse/releases/latest/download/lighthouse-native-linux-amd64.zip
$ unzip lighthouse-native-linux-amd64.zip
$ ./lh
** ERROR: **
No sub-command specified.


NAME:
 lh - A tool for reading and combining EDN hierarchies

USAGE:
 lh [global-options] command [command options] [arguments...]

VERSION:
 v2.0.1

COMMANDS:
   build                Generates output manifests for all provided files.
   update               Updates 1-to-many values in 1-to-many given files
   visualize            Renders a graphvis visualization of the provided file

GLOBAL OPTIONS:
   -?, --help
```

## Extensible Data Notation

Lighthouse's data is in the Extensible Data Notation (EDN) format, which is
a subset of the Clojure syntax. It is to Clojure what JSON is to JavaScript.

One useful benefit over JSON is that comments are supported.

For a quick primer into the format, check out [this guide](https://learnxinyminutes.com/docs/edn/).

## First manifest

The most common task to automate with Lighthouse is generating Kubernetes
manifests, so we'll start with a simple pod.

First, make a directory for your Lighthouse data and change into it:

```
mkdir tutorial
cd tutorial
```

### Files

Only two files are needed to generate the Kubernetes yaml for a pod.

The first is the Lighthouse config itself. This file anchors the base of the
Lighthouse data and is used as a reference point when searching for parent
libraries to use.

#### File 1: Lighthouse config file for Kubernetes

Create the file `lighthouse.edn`:

```clojure
{:output-format :yaml}
```

This sets the output format to be YAML (as opposed to JSON or EDN).

#### File 2: Library file for the pod itself

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

### Run Lighthouse

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

[Parent Libraries :>](/doc/tutorial/02-libraries.md)
