FROM ubuntu:latest
WORKDIR /app

RUN apt update && \
    apt install --no-install-recommends -yy curl unzip ca-certificates

ARG LH_VERSION
RUN test -n "$LH_VERSION" && \
    curl -sLO https://github.com/barracudanetworks/lighthouse/releases/download/$LH_VERSION/lighthouse-native-linux-amd64.zip && \
    unzip lighthouse-native-linux-amd64.zip && \
    mv lh /usr/local/bin && \
    rm lighthouse-native-linux-amd64.zip

ENTRYPOINT ["lh"]
CMD ["--help"]
