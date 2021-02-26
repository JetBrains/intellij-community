#!/bin/bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )";

cd "$DIR/../work"

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
git pull
yarn install

export CONTENT_ROOT="$DIR/../work/mdn-content/files"
yarn build:client
yarn build:ssr
yarn build

echo "Done!"