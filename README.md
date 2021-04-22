# hello-java-sigstore

The start of a Java and Sigstore demo.

Based on (and heavily inspired by!) Ivan Font and Bob Callaway's fantastic <https://github.com/redhat-et/sigstore-demo>

## Developing/hacking

### Build a container image from your local machine

To run the build on your local machine (to check everything works):

    export REGISTRY_USERNAME=youruser@example.com
    export REGISTRY_PASSWORD=registrypassssssswd

    mvn compile jib:build

    mvn compile jib:build -Djib.to.image=quay.io/youruser/yourimage:latest \
        -Djib.to.auth.username=$REGISTRY_USERNAME \
        -Djib.to.auth.password=$REGISTRY_PASSWORD

## Signing and verification with Rekor

These steps will build the application into a JAR, sign it, and then add the signature into [Rekor][rekor].

**Rekor** is a log for storing signed metadata; things like signatures for artifacts or container images.

### Deploy Rekor on OpenShift

_These instructions are for OpenShift only._

The Redis and Mysql container images must be run as a specific user, so you will need to give the `anyuid` SCC to the `default` service account, before you create the OpenShift resources. 

Run these two commands to grant `anyuid` and create the OpenShift objects to deploy a full Rekor instance, including a `BuildConfig` to build the _rekor-server_ app from source:

    oc adm policy add-scc-to-user anyuid -z default
    oc apply -f ./contrib/rekor/openshift.yaml

_rekor-server_ should now be exposed as a _Route_:

    $ oc get route 
    NAME           HOST/PORT                                      PATH   SERVICES       PORT   TERMINATION     WILDCARD
    rekor-server   rekor-server-toms-temp.apps.ocp1.example.com          rekor-server   3000   edge/Redirect   None

### Install rekor-cli

Install rekor-cli. See [the official build instructions][cli], you might need to do `export GO111MODULE=on` first.

#### Build the JAR and sign with GPG

Create a GPG identity:

    gpg --quick-generate-key bobby@example.com

Build the app and sign it with your GPG identity:

    mvn clean package

    gpg --armor -u bobby@example.com --output artifact.asc --detach-sig target/hello-java-1.0.0-SNAPSHOT.jar

#### Add the signature to Rekor

To create an entry in Rekor, you need to give it:

- an artifact

- the signature of the artifact

- a GPG public key

So, push the JAR, the signature and your public key to your Rekor instance on OpenShift:

    export REKOR_API=https://route-to-your-rekor-server.ocp1.example.com

    gpg --export --armor bobby@example.com > bobby.pub

    rekor upload --rekor_server ${REKOR_API} \
        --signature artifact.asc \
        --public-key bobby.pub \
        --artifact target/hello-java-1.0.0-SNAPSHOT.jar

#### Verify the signature

You can also verify the signature in Rekor. You'll need the signature, the public key and the JAR (which you should already have, if you just ran the steps above):

    rekor verify --rekor_server ${REKOR_API} \
        --signature artifact.asc \
        --public-key bobby.pub \
        --artifact target/hello-java-1.0.0-SNAPSHOT.jar

#### Search for the artifact

You can also search the Rekor log using the SHA256 of the artifact:

    sha256=`sha256sum target/hello-java-1.0.0-SNAPSHOT.jar | awk '{ print $1 }'`

    rekor search --rekor_server ${REKOR_API} --sha $sha256

Should return:

> Found matching entries (listed by UUID):  
> 8c6b9e94c12a918d9fcdc9adbfd36ea9dfb8b2ca776629a0a281907bee971337

Then you can pop that UUID into `rekor get`:

    rekor get --rekor_server ${REKOR_API} --uuid <the uuid>

Which returns the sha256 of the artifact, along with the signature and the public key used to sign it.

#### Test a bad signature

You can also test an invalid/evil signature:

    echo "all your supply chain is belong to us!!!!1" >> badfile.txt

    gpg --armor -u bobby@example.com --output wrongun.asc --detach-sig badfile.txt

    rekor verify --rekor_server ${REKOR_API} \
        --signature wrongun.asc \
        --public-key bobby.pub \
        --artifact target/hello-java-1.0.0-SNAPSHOT.jar

Which should generate an error like this:

> 2021/04/21 16:52:03 [POST /api/v1/log/entries/retrieve][500] searchLogQuery default  &{Code:500 Message:openpgp: invalid signature: hash tag doesn't match}

**Your signature was BAD, mmkay.**

## CI/CD

This is a work-in-progress at the moment.

### Install the Tekton pipeline

Tekton may have already installed a _ClusterTask_ called `jib-maven`. However we want to use the latest version (0.3) and some of the parameters have changed. So we'll install the current `jib-maven` task in our local namespace and use that instead of the shared _ClusterTask_.

First create a _Secret_ with authentication details for your container registry, and link it to the `pipeline` service account:

```
oc create secret docker-registry docker-hub \
  --docker-server=docker.io \
  --docker-username=yourusername \
  --docker-password=yourpassword

oc secrets link pipeline docker-hub --for=mount
```

To install the pipeline:

```
# Install the jib-maven community task for Tekton
oc apply -f https://raw.githubusercontent.com/tektoncd/catalog/main/task/jib-maven/0.3/jib-maven.yaml

oc apply -f .tekton/pipeline.yml
```

Start the pipeline (create a `PipelineRun`):

```
oc create -f .tekton/run.yml
```

[cli]: https://sigstore.dev/get_started/client/
[rekor]: https://github.com/sigstore/rekor
