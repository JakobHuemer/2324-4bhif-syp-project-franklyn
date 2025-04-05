FROM rust:1.86-alpine3.21

RUN <<EOF
apk update
apk add git wget musl tree python3 meson ninja-build make gcc automake autoconf libtool musl-dev pkgconf fuse
rustup target add x86_64-unknown-linux-musl
wget https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-x86_64.AppImage
chmod +x linuxdeploy-x86_64.AppImage
EOF
