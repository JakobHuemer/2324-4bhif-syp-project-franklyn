#!/usr/bin/env bash

set -ex

_dir=$(realpath $(dirname $0))

git submodule update --init --recursive

docker build -t openbox-builder/ubuntu $_dir
docker run -v $_dir/../../:/franklyn openbox-builder/ubuntu /bin/bash -c "
cd /franklyn/openbox/
RUSTFLAGS='-C target-feature=-crt-static' cargo build --profile release-opt --package openbox --features $1
"
