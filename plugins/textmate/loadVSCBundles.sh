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
    rm -rf "$ROOT/lib/bundles/$f/cgmanifest.json"
    find "$ROOT/lib/bundles/$f/" -name "*.js" -type f -delete
    find "$ROOT/lib/bundles/$f/" -name "*.ts" -type f -delete
    find "$ROOT/lib/bundles/$f/" -name "*.png" -type f -delete
  fi
done
cd ../..

# vim script
echo "Adding vim script"
git clone https://github.com/AlexPl292/language-viml
cd language-viml

mkdir -p "$ROOT/lib/bundles/viml"
cp -r "LICENSE.txt" "$ROOT/lib/bundles/viml"
cp -r "package.json" "$ROOT/lib/bundles/viml"
cp -r "grammars" "$ROOT/lib/bundles/viml"

cd ..

# mdx
git clone https://github.com/mdx-js/mdx-analyzer
cd mdx-analyzer/packages/vscode-mdx

echo "Adding mdx"
mkdir -p "$ROOT/lib/bundles/mdx"
cp -r "package.json" "$ROOT/lib/bundles/mdx/"
cp -r "language-configuration.json" "$ROOT/lib/bundles/mdx/"
cp -r "license" "$ROOT/lib/bundles/mdx/"
cp -r "syntaxes" "$ROOT/lib/bundles/mdx/"

cd ../../..

#twig
echo "Adding twig"
git clone https://github.com/mblode/vscode-twig-language-2
cd vscode-twig-language-2

mkdir -p "$ROOT/lib/bundles/twig"
cp -r "LICENSE.md" "$ROOT/lib/bundles/twig"
cp -r "package.json" "$ROOT/lib/bundles/twig"
cp -r "README.md" "$ROOT/lib/bundles/twig"
cp -r "src" "$ROOT/lib/bundles/twig"
cp -r "src/snippets" "$ROOT/lib/bundles/twig" # TODO: remove after updating to the next version

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

cd ..

# terraform
git clone https://github.com/hashicorp/vscode-terraform
cd vscode-terraform

echo "Adding terraform"
mkdir -p "$ROOT/lib/bundles/terraform"
cp -r "LICENSE" "$ROOT/lib/bundles/terraform"
cp -r "package.json" "$ROOT/lib/bundles/terraform"
cp -r "language-configuration.json" "$ROOT/lib/bundles/terraform"
cp -r "README.md" "$ROOT/lib/bundles/terraform"
cp -r "snippets" "$ROOT/lib/bundles/terraform"

cd ..

# asciidoc
git clone https://github.com/asciidoctor/asciidoctor-vscode
cd asciidoctor-vscode

echo "Adding asciidoc"
mkdir -p "$ROOT/lib/bundles/adoc"
cp -r "LICENSE" "$ROOT/lib/bundles/adoc"
cp -r "package.json" "$ROOT/lib/bundles/adoc"
cp -r "asciidoc-language-configuration.json" "$ROOT/lib/bundles/adoc"
cp -r "README.md" "$ROOT/lib/bundles/adoc"
cp -r "syntaxes" "$ROOT/lib/bundles/adoc"
cp -r "snippets" "$ROOT/lib/bundles/adoc"

cd ..

# hcl
git clone https://github.com/hashicorp/vscode-hcl
cd vscode-hcl

echo "Adding hcl"
mkdir -p "$ROOT/lib/bundles/hcl"
cp -r "LICENSE" "$ROOT/lib/bundles/hcl"
cp -r "package.json" "$ROOT/lib/bundles/hcl"
cp -r "language-configuration.json" "$ROOT/lib/bundles/hcl"
cp -r "README.md" "$ROOT/lib/bundles/hcl"

cd ..

git clone https://github.com/twxs/vs.language.cmake
cd vs.language.cmake

echo "Adding CMake"
mkdir -p "$ROOT/lib/bundles/cmake"
cp -r "LICENSE" "$ROOT/lib/bundles/cmake"
cp -r "package.json" "$ROOT/lib/bundles/cmake"
cp -r "README.md" "$ROOT/lib/bundles/cmake"
cp -r "syntaxes" "$ROOT/lib/bundles/cmake"

cd ..

mkdir -p "$ROOT/lib/bundles/terraform/syntaxes"
wget -q https://raw.githubusercontent.com/hashicorp/syntax/main/syntaxes/terraform.tmGrammar.json -O "$ROOT/lib/bundles/terraform/syntaxes/terraform.tmGrammar.json"
wget -q https://raw.githubusercontent.com/hashicorp/syntax/main/syntaxes/hcl.tmGrammar.json -O "$ROOT/lib/bundles/terraform/syntaxes/hcl.tmGrammar.json"

mkdir -p "$ROOT/lib/bundles/hcl/syntaxes"
wget -q https://raw.githubusercontent.com/hashicorp/syntax/main/syntaxes/hcl.tmGrammar.json -O "$ROOT/lib/bundles/hcl/syntaxes/hcl.tmGrammar.json"

echo "Adding erlang"
mkdir -p "$ROOT/lib/bundles/erlang/grammar"
wget -q https://raw.githubusercontent.com/erlang-ls/vscode/main/language-configuration.json -O "$ROOT/lib/bundles/erlang/language-configuration.json"
wget -q https://raw.githubusercontent.com/erlang-ls/vscode/main/package.json -O "$ROOT/lib/bundles/erlang/package.json"
wget -q https://raw.githubusercontent.com/erlang-ls/grammar/main/Erlang.plist -O "$ROOT/lib/bundles/erlang/grammar/Erlang.plist"

# Kconfig
git clone https://github.com/trond-snekvik/vscode-kconfig/
pushd vscode-kconfig

echo "Adding KConfig"
declare kconfig_target="$ROOT/lib/bundles/kconfig"
declare -a kconfig_resources=("LICENSE" "package.json" "language-configuration.json" "syntaxes")
mkdir -p "$kconfig_target"
for resource in ${kconfig_resources[@]}
do
  cp -r "$resource" "$kconfig_target"
done

popd

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