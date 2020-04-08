#! /bin/bash

set -o errexit

ROOT=$PWD

rm -rf temp
mkdir temp
if [ ! -d lib/bundles ]; then
  mkdir lib/bundles
fi

cd temp

# vscode languages
git clone https://github.com/Microsoft/vscode

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

# mdx
git clone https://github.com/silvenon/vscode-mdx.git
cd vscode-mdx

echo "Adding mdx"
mkdir -p "$ROOT/lib/bundles/mdx"
cp -r "package.json" "$ROOT/lib/bundles/mdx/"
cp -r "license" "$ROOT/lib/bundles/mdx/"
cp -r "syntaxes" "$ROOT/lib/bundles/mdx/"

cd ..

# kotlin
git clone https://github.com/sargunv/kotlin-textmate-bundle.git
cd kotlin-textmate-bundle/Kotlin.tmbundle
mv "Snippets" "snippets"
mv "Syntaxes" "syntaxes"

echo "Adding kotlin"
mkdir -p "$ROOT/lib/bundles/kotlin"
cp -r "info.plist" "$ROOT/lib/bundles/kotlin/"
cp -r "snippets" "$ROOT/lib/bundles/kotlin/"
cp -r "syntaxes" "$ROOT/lib/bundles/kotlin/"

cd ../..


# end
rm -rf $ROOT/temp