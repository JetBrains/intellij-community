#! /bin/bash

set -o errexit

ROOT=$PWD

rm -rf temp
mkdir temp
if [ ! -d lib/bundles ]; then
  mkdir lib/bundles
fi

cd temp

git clone https://github.com/Microsoft/vscode
# cp -r /Users/denofevil/WebstormProjects/vscode .

cd vscode/extensions
for f in *; do
  if [ -d "$f/syntaxes" ]; then
    echo "Adding $f"
    cp -r "$f" "$ROOT/lib/bundles"
    rm -rf "$ROOT/lib/bundles/$f/test"
    rm -rf "$ROOT/lib/bundles/$f/build"
    rm -rf "$ROOT/lib/bundles/$f/resources"
  fi
done

cd ../..
git clone https://github.com/silvenon/vscode-mdx.git
cd vscode-mdx

echo "Adding mdx"
mkdir -p "$ROOT/lib/bundles/mdx"
cp -r "package.json" "$ROOT/lib/bundles/mdx/"
cp -r "license" "$ROOT/lib/bundles/mdx/"
cp -r "syntaxes" "$ROOT/lib/bundles/mdx/"

rm -rf $ROOT/temp