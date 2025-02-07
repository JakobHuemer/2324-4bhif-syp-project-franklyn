#!/usr/bin/env bash

_dir=$(dirname $0)

cd $_dir

cnt=0

if test $# -ne 2; then
   echo "Please supply two arguments: $(basename $0) <amount> <pin>"
   exit 1
fi

cargo build -r --features no_screencap

while test $cnt -lt $1; do
   ./target/release/openbox -p $2 -f stress -l bot$cnt -a &
   cnt=$((cnt + 1))
done
