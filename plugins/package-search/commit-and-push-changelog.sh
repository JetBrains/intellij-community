#!/usr/bin/env bash
# Copyright 2000-2022 JetBrains s.r.o. and contributors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
set -e -x -u

if [ $# -eq 0 ]; then
  echo "Please provide the version name as the first argument and the git repo root path as the second argument"
  exit 1
fi
if [ -z "$1" ]; then
  echo "The version name must not be blank."
  exit 1
fi
if [ -z "$2" ]; then
  echo "The git repo root path must not be blank."
  exit 1
fi

version=$1
repoPath=$2

if [ ! -d "$repoPath" ]; then
  echo "The git repo root path must be a directory."
  exit 2
fi

cd "$2"

git add "intellij-plugin/CHANGELOG.md"
git status

if git diff-index --quiet HEAD --; then
  echo 'The changelog has NOT been updated. Are you sure you''re ready to cut a new plugin version?'
  exit 2
fi

git config user.email ps-buildbot@kpm.jetbrains.com
git config user.name 'Package Search buildbot'

git commit -m "Update changelog for version $version"

upstream=$(git rev-parse --abbrev-ref --symbolic-full-name @{u})
if [ -z "$upstream" ]; then
  echo "The current branch does not track a remote branch, cannot push."
  exit 3
fi

git format-patch -1 HEAD --stdout
#git pull origin || true
#git push origin
