#!/usr/bin/env bash

_dir=$(realpath $(dirname $0))

cd $_dir
RUSTFLAGS="-C target-feature=-crt-static" cargo build --profile release-opt --package openbox
#-vv
