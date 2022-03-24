#! /bin/bash

set -o errexit

ROOT=$PWD

if ! command -v jq &> /dev/null
then
    echo "jq (https://stedolan.github.io/jq/) could not be found, please install jq"
    exit
fi

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
    rm -rf "$ROOT/lib/bundles/$f/src"
    rm -rf "$ROOT/lib/bundles/$f/resources"
    rm -rf "$ROOT/lib/bundles/$f/yarn.lock"
    find "$ROOT/lib/bundles/$f/" -name "*.js" -type f -delete
    find "$ROOT/lib/bundles/$f/" -name "*.ts" -type f -delete
    find "$ROOT/lib/bundles/$f/" -name "*.png" -type f -delete
  fi
done
cd ../..

# vim script
echo "Adding vim script"
git clone git@github.com:AlexPl292/language-viml.git
cd language-viml

mkdir -p "$ROOT/lib/bundles/viml"
cp -r "LICENSE.txt" "$ROOT/lib/bundles/viml"
cp -r "package.json" "$ROOT/lib/bundles/viml"
cp -r "grammars" "$ROOT/lib/bundles/viml"

cd ..

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

cd ..

# jsp
git clone https://github.com/pthorsson/vscode-jsp
cd vscode-jsp

echo "Adding jsp"
mkdir -p "$ROOT/lib/bundles/jsp"
cp -r "LICENSE" "$ROOT/lib/bundles/jsp"
cp -r "package.json" "$ROOT/lib/bundles/jsp"
cp -r "jsp-configuration.json" "$ROOT/lib/bundles/jsp"
cp -r "README.md" "$ROOT/lib/bundles/jsp"
cp -r "syntaxes" "$ROOT/lib/bundles/jsp"

cd $ROOT

rm -rf $ROOT/temp

echo "Applying patch"
git apply $ROOT/bundles.patch

echo "Cleaning up package.json"
cd $ROOT/lib/bundles
for f in *; do
  if [ -f "$f/package.json" ]; then
    cat "$f/package.json" | jq "{name: .name, version: .version, description: .description, license: .license, contributes: .contributes}" >"$f/package.patched.json"
    mv "$f/package.patched.json" "$f/package.json"
  fi
done

cd $ROOT