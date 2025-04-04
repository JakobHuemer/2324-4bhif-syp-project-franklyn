#!/usr/bin/env bash

set -ex

_dir=$(dirname $0)

cd $_dir

# Create AppDir
appdir=openbox.AppDir

mkdir -p $appdir/usr/bin/
mkdir -p $appdir/usr/lib/
mkdir -p $appdir/usr/share/applications/
mkdir -p $appdir/usr/share/icons/hicolor/256x256/apps/

# compile and copy binary to AppDir
cargo build --profile release-opt --package openbox
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

