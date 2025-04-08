FROM ubuntu:20.04

RUN <<EOF
export DEBIAN_FRONTED=noninteractive
apt-get update
DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get -y install tzdata
apt-get install -y build-essential curl wget libssl-dev autoconf python3 git meson pkg-config python3 ninja-build libtool
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
. "$HOME/.cargo/env" 
rustup default stable
EOF

ENV PATH="/root/.cargo/bin:${PATH}"
