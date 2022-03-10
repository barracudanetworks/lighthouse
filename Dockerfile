# this dockerfile and the associated compilation steps are
# highly inspired by babashka's compilation process.
#
# See below for more details:
#   https://github.com/babashka/babashka
FROM clojure:openjdk-11-lein-2.9.6-bullseye AS BASE

ENV DEBIAN_FRONTEND=noninteractive
RUN apt update
RUN apt install --no-install-recommends -yy curl unzip build-essential zlib1g-dev sudo
WORKDIR "/opt"
RUN curl -sLO https://github.com/graalvm/graalvm-ce-builds/releases/download/vm-21.1.0/graalvm-ce-java11-linux-amd64-21.1.0.tar.gz
RUN tar -xzf graalvm-ce-java11-linux-amd64-21.1.0.tar.gz

ENV GRAALVM_HOME="/opt/graalvm-ce-java11-21.1.0"
ENV JAVA_HOME="/opt/graalvm-ce-java11-21.1.0/bin"
ENV PATH="$JAVA_HOME:$PATH"
ENV IS_STATIC="true"
ENV USE_MUSL="true"

COPY . .
RUN ./script/setup-musl
RUN ./script/uberjar
RUN ./script/compile

FROM ubuntu:latest
COPY --from=BASE /opt/lh /usr/local/bin/lh
ENTRYPOINT ["lh"]
CMD ["--help"]
