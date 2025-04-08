#!/usr/bin/env bash

set -ex

_dir=$(realpath $(dirname $0))

docker build -t openbox-builder/ubuntu $_dir
docker run -it -v $_dir/../../:/franklyn openbox-builder/ubuntu /bin/bash /franklyn/openbox/build.sh
#docker run -it -v $_dir/../../:/franklyn openbox-builder/alpine /bin/ash -c 'find /lib /usr/lib -name "libc.a"'
