#!/usr/bin/env bash

set -ex

_dir=$(realpath $(dirname $0))

git submodule update --init --recursive

docker build -t openbox-builder/ubuntu $_dir
docker run -it -v $_dir/../../:/franklyn openbox-builder/ubuntu /bin/bash -c "
cd /franklyn/openbox/
RUSTFLAGS='-C target-feature=-crt-static' cargo build --profile release-opt --package openbox --features $1
"

cd $_dir/..
#
# Create AppDir
appdir=openbox.AppDir

mkdir -p $appdir/usr/bin/
mkdir -p $appdir/usr/lib/
mkdir -p $appdir/usr/share/applications/
mkdir -p $appdir/usr/share/icons/hicolor/256x256/apps/

# copy binary to AppDir
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

# download linuxdeplay
# cleanup previous image
rm -f linuxdeploy-x86_64.AppImage
wget https://github.com/linuxdeploy/linuxdeploy/releases/download/continuous/linuxdeploy-x86_64.AppImage
chmod +x linuxdeploy-x86_64.AppImage

NO_STRIP=true ./linuxdeploy-x86_64.AppImage --appdir $appdir --executable $appdir/usr/bin/openbox --output appimage

# cleanup
rm -f linuxdeploy-x86_64.AppImage
rm -rf $appdir
