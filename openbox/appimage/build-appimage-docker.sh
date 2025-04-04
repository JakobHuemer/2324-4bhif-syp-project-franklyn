#!/usr/bin/env bash

_dir=$(realpath $(dirname $0))

docker build -t openbox-builder/arch $_dir
docker run -it -v $_dir/../:/franklyn openbox-builder/arch /bin/bash -c '
set -ex

cd franklyn

_dir=$(pwd)

bdir=$_dir/build
rm -rf $bdir
mkdir -p $bdir

pfx=/openbox-libs
mkdir -p $pfx

# set c compiler to musl
export CC=musl-gcc

# export new package config and autoconf (aclocal) paths to the install prefix
export PKG_CONFIG_PATH="$pfx/share/pkgconfig:$pfx/usr/local/share/pkgconfig:$pfx/usr/local/lib/pkgconfig:$PKG_CONFIG_PATH"
export ACLOCAL="aclocal -I ${pfx}/share/aclocal" 

# build xorg-macros
cd $bdir
git clone https://gitlab.freedesktop.org/xorg/util/macros.git
cd macros
git switch --detach util-macros-1.20.2

./autogen.sh
./configure --disable-shared --enable-static --host=x86_64-linux-musl --prefix=$pfx
make -j$(nproc --all)
make install

# build xcb-proto
cd $bdir
git clone https://gitlab.freedesktop.org/xorg/proto/xcbproto.git
cd xcbproto
git switch --detach xcb-proto-1.17.0

./autogen.sh
./configure --disable-shared --enable-static --host=x86_64-linux-musl --prefix=$pfx
make -j$(nproc --all)
make install

# build xorgproto
cd $bdir
git clone https://gitlab.freedesktop.org/xorg/proto/xorgproto.git
cd xorgproto
git switch --detach xorgproto-2024.1

meson setup build --default-library=static
meson compile -C build
meson install -C build --destdir "$pfx"

export CFLAGS="-I$pfx/usr/local/include"

# build xau
cd $bdir
git clone https://gitlab.freedesktop.org/xorg/lib/libxau.git
cd libxau
git switch --detach libXau-1.0.12

meson setup build --default-library=static
meson compile -C build
meson install -C build --destdir "$pfx"

# build xcb
cd $bdir
git clone https://gitlab.freedesktop.org/xorg/lib/libxcb.git
cd libxcb
git switch --detach libxcb-1.17.0

./autogen.sh
./configure --disable-shared --enable-static --host=x86_64-linux-musl --prefix=$pfx
make -j$(nproc --all)
make install

find $pfx -iname "*.a"

# Create AppDir
appdir=$bdir/openbox.AppDir

mkdir -p $appdir/usr/bin/
mkdir -p $appdir/usr/lib/
mkdir -p $appdir/usr/share/applications/
mkdir -p $appdir/usr/share/icons/hicolor/256x256/apps/

# compile and copy binary to AppDir
cd $_dir
RUSTFLAGS="-L $pfx/lib/ -L $pfx/usr/local/lib/ -Ctarget-feature=-crt-static -Clink-args=-lm -Clink-args=-lXau -Clink-args=-static" cargo build --profile release-opt --package openbox --target x86_64-unknown-linux-musl
cp ./target/release-opt/openbox $appdir/usr/bin/openbox

# create desktop entry
cat <<EOF > $appdir/usr/share/applications/openbox.desktop
[Desktop Entry]
Name=Franklyn Openbox
Exec=openbox
Icon=openbox
Type=Application
Categories=Education;
EOF

# copy logo
cp ../asciidocs/logo.png $appdir/usr/share/icons/hicolor/256x256/apps/openbox.png

NO_STRIP=true /linuxdeploy-x86_64.AppImage --appdir $appdir --executable $appdir/usr/bin/openbox --output appimage
'
