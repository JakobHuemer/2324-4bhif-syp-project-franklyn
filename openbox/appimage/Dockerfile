FROM archlinux:base-devel-20250330.0.328921 

RUN <<EOF
pacman -Syu --needed --noconfirm git rustup wget musl tree python meson ninja
rustup default stable
rustup target add x86_64-unknown-linux-musl
wget https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-x86_64.AppImage
chmod +x linuxdeploy-x86_64.AppImage
EOF
