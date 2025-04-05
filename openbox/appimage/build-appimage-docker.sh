#!/usr/bin/env bash

_dir=$(realpath $(dirname $0))

docker build -t openbox-builder/alpine $_dir
docker run -it -v $_dir/../../:/franklyn openbox-builder/alpine /bin/ash /franklyn/openbox/appimage/build.sh
#docker run -it -v $_dir/../../:/franklyn openbox-builder/alpine /bin/ash -c 'find /lib /usr/lib -name "libc.a"'
