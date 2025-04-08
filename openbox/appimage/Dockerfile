FROM ubuntu:24.04

RUN <<EOF
apt-get update
apt-get install -y build-essential curl wget libssl-dev autoconf python3 git meson pkg-config python3 python3-pip python3-setuptools python3-wheel ninja-build libtool
pip3 install --user meson
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
. "$HOME/.cargo/env" 
rustup default stable
EOF

ENV PATH="/root/.cargo/bin:${PATH}"
