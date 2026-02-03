#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )";

cd "$DIR/../baseline-server"
npm install

mkdir -p "$DIR/../work"

cd "$DIR/../work"

rm -f browser-compat-data.json
wget "https://unpkg.com/@mdn/browser-compat-data/data.json" -O browser-compat-data.json

if [ ! -d "mdn-content" ]; then
  git clone https://github.com/mdn/content.git mdn-content
fi

cd "mdn-content"
git pull
yarn install
cd ..

if [ ! -d "yari" ]; then
  git clone https://github.com/mdn/yari.git yari
fi

cd "yari"
git reset --hard
git pull
rm -Rf cloud-function
yarn install

export CONTENT_ROOT="$DIR/../work/mdn-content/files"
yarn build:prepare
yarn build

echo "Done!"