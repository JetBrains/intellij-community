#! /bin/bash

set -o errexit

ROOT=$PWD
PATCHES="$ROOT/patches"

rm -rf temp
rm -rf result
mkdir temp
cd temp

echo ">>>>> Preparing validator build"
git clone https://github.com/validator/validator
cd validator
cd build
git apply "$PATCHES/patch_build.patch"
cd ..

echo
echo ">>>>> Building schema drivers"
python build/build.py build

echo
echo ">>>>> Collecting schemas"
cd ../..
mkdir result
mkdir result/html5
cp temp/validator/schema/*.rnc result
rsync -r --include=*.rnc temp/validator/schema/* result
rm -rf result/xhtml10
ruby html5charref.rb > result/html5chars.ent

echo
echo ">>>>> Patching html5 schema"
cp patches/*.rnc result/html5
cd result
for f in `ls $PATCHES`
do
  if [[ "$f" == 0*\.* ]]
  then
	patch -p0 -u < "$PATCHES/$f"
  fi
done
cd ..

echo
echo ">>>>> Moving items in place"
cp result/html5-all.rnc result/html5.rnc
cp result/xhtml5-all.rnc result/xhtml5.rnc
mv result/html5/LICENSE result
rm -rf html5-schema
mv result html5-schema
rm -rf temp
