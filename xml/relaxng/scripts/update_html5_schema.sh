#! /bin/bash

set -o errexit

ROOT=$PWD
PATCHES="$ROOT/../resources/resources/patches"

cd ../resources/resources

rm -rf temp
rm -rf result
rm -rf browser-compat-data
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
python3 build/build.py build

echo
echo ">>>>> Collecting schemas"
cd ../..
mkdir result
mkdir result/html5
rsync -r --include=*.rnc temp/validator/schema/* result
rm -rf result/xhtml10
pwd
ruby ../../scripts/html5charref.rb > result/html5chars.ent

echo
echo ">>>>> Trimming line endings in result directory"
find result -type f -name "*.rnc" -exec sh -c "sed -i.bak 's/[[:space:]]*$//' {} && rm {}.bak" _ {} \;

echo
echo ">>>>> Patching html5 schema"
cp patches/*.rnc result/html5
cd result
for f in `ls $PATCHES`
do
  if [[ "$f" == 0*\.* ]]
  then
  echo "$f"
  patch -p0 -u < "$PATCHES/$f"
  fi
done
cd ..

echo
echo ">>>>> Moving items in place"
mv result/html5/LICENSE result

# Use our root schema files
cp html5-schema/*.rnc result

# Retain our SVG 2.0 support
cp -R html5-schema/svg20 result

# Retain our rdf support
cp -R html5-schema/rdf result

# Remove unnecessary files
rm result/html5/.htaccess
rm result/mml3/Makefile
rm result/mml3/patch-vnu

# Update html5-schema dir
rm -rf html5-schema
mv result html5-schema
rm -rf temp
