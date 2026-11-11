#!/usr/bin/env bash
# Merges a newer version of https://github.com/microsoft/debugpy into this directory.
#
# Usage: update_debugpy.sh [<upstream-tag-or-commit>]
#        update_debugpy.sh -h | --help
#
# The default target is the latest upstream release tag.
# The script does a three-way merge. The base is the upstream commit in "git_full" of _version.py,
# "ours" is this directory at HEAD, and "theirs" is the target.
# It writes the result into this directory and stages it. It does not commit.
# Conflicted files stay unmerged in the index, as after "git merge". Resolve them manually.
# The merge ignores the upstream files that our copy does not have, for example the pydevd tests.
# The upstream clone is in $DEBUGPY_UPDATE_WORK_DIR, or ~/.cache/intellij-debugpy-update.

set -euo pipefail

UPSTREAM_URL="https://github.com/microsoft/debugpy.git"
UPSTREAM_DIR="src/debugpy"
WORK_DIR="${DEBUGPY_UPDATE_WORK_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/intellij-debugpy-update}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
HELPER_DIR="$(git -C "$SCRIPT_DIR" rev-parse --show-prefix)"
HELPER_DIR="${HELPER_DIR%/}"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
# Pathspec that excludes this script, so that the local copy of the script stays as it is.
NOT_SCRIPT=":(exclude)$HELPER_DIR/$SCRIPT_NAME"

die() {
  echo "error: $*" >&2
  exit 1
}

work_git() {
  git -C "$WORK_DIR" -c user.name="debugpy update" -c user.email="debugpy-update@localhost" "$@"
}

repo_git() {
  git -C "$REPO_ROOT" "$@"
}

# Prints the value of a versioneer keyword (git_refnames, git_full or git_date) from _version.py on stdin.
read_version_keyword() {
  sed -n "s/^    $1 = \"\(.*\)\"$/\1/p"
}

# Sets the versioneer keywords in a _version.py file. The arguments are the file, refnames, full hash and date.
set_version_keywords() {
  local file="$1"
  sed -e "s|^    git_refnames = .*|    git_refnames = \"$2\"|" \
      -e "s|^    git_full = .*|    git_full = \"$3\"|" \
      -e "s|^    git_date = .*|    git_date = \"$4\"|" \
      "$file" >"$file.tmp"
  mv "$file.tmp" "$file"
}

# Adds also the ignored files. The upstream .gitignore files ignore some of the files that we ship.
add_all() {
  work_git add -A -f -- "$UPSTREAM_DIR"
}

# 1. Get the upstream repository.

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  # Print the comment at the top of this file.
  awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"
  exit 0
fi
[[ $# -le 1 && "${1:-}" != -* ]] || die "usage: $SCRIPT_NAME [<upstream-tag-or-commit>]. See '$SCRIPT_NAME --help'."
if [[ -n "$(repo_git status --porcelain --untracked-files=all -- "$HELPER_DIR" "$NOT_SCRIPT")" ]]; then
  die "$HELPER_DIR has uncommitted or untracked changes. Commit or remove them first."
fi
[[ -z "$(repo_git ls-files -u)" ]] || die "the index has unmerged files. Resolve them first."

if [[ ! -d "$WORK_DIR/.git" ]]; then
  echo "Cloning $UPSTREAM_URL into $WORK_DIR"
  mkdir -p "$(dirname "$WORK_DIR")"
  git clone -q --filter=blob:none --no-checkout "$UPSTREAM_URL" "$WORK_DIR"
else
  echo "Fetching $UPSTREAM_URL into $WORK_DIR"
  work_git fetch -q --tags --force origin
fi
WORK_GIT_DIR="$(work_git rev-parse --absolute-git-dir)"
work_git config merge.conflictStyle zdiff3
# Copy the files unchanged: no "$Format$" substitution and no skipped files.
mkdir -p "$WORK_GIT_DIR/info"
echo "* -export-subst -export-ignore" >"$WORK_GIT_DIR/info/attributes"

target="${1:-}"
if [[ -z "$target" ]]; then
  target="$(work_git tag -l 'v*' --sort=-version:refname | { grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' || true; } | sed -n 1p)"
  [[ -n "$target" ]] || die "cannot find a release tag in $UPSTREAM_URL"
fi
new="$(work_git rev-parse --verify -q "$target^{commit}")" || die "unknown upstream tag or commit: $target"
base="$(repo_git show "HEAD:$HELPER_DIR/_version.py" | read_version_keyword git_full)"
[[ "$base" =~ ^[0-9a-f]{40}$ ]] || die "cannot read git_full from $HELPER_DIR/_version.py"
work_git cat-file -e "$base^{commit}" 2>/dev/null || die "the upstream repository has no commit $base"
[[ "$base" != "$new" ]] || die "$HELPER_DIR is already based on $target"
work_git merge-base --is-ancestor "$base" "$new" || die "$target does not contain the upstream base $base"
echo "Upstream base: $(work_git describe --tags --always "$base") ($base)"
echo "Upstream target: $target ($new)"

# 2. Build three branches in the upstream clone:
#   upstream-base  The base, without the upstream files that our copy does not have.
#   jetbrains      Our copy, on top of upstream-base.
#   upstream-new   The target, without the same files. It is a child of upstream-base,
#                  so upstream-base is the merge base of jetbrains and upstream-new.

work_git checkout -q -f --detach "$base"
work_git clean -q -f -d -x
work_git rm -r -q --cached -- "$UPSTREAM_DIR"
rm -rf "${WORK_DIR:?}/$UPSTREAM_DIR"
mkdir -p "$WORK_DIR/$UPSTREAM_DIR"
repo_git archive --format=tar "HEAD:$HELPER_DIR" | tar -x -f - -C "$WORK_DIR/$UPSTREAM_DIR"
add_all
ours_tree="$(work_git write-tree)"

# The files to strip: for each upstream file that our copy does not have,
# the top directory that our copy does not have, or the file itself.
strip_list="$WORK_GIT_DIR/debugpy-update-strip-list"
work_git diff-tree -r --name-only --diff-filter=D "$base" "$ours_tree" -- "$UPSTREAM_DIR" | while IFS= read -r path; do
  parent="$(dirname "$path")"
  while [[ "$parent" != "$UPSTREAM_DIR" ]] && ! work_git cat-file -e "$ours_tree:$parent" 2>/dev/null; do
    path="$parent"
    parent="$(dirname "$path")"
  done
  echo "$path"
done | sort -u >"$strip_list"

version_file="$WORK_DIR/$UPSTREAM_DIR/_version.py"
ours_version="$(work_git show "$ours_tree:$UPSTREAM_DIR/_version.py")"

# The base gets the versioneer keywords of our copy, and the target gets its own keywords.
# Then the merge takes the keywords of the target without a conflict.
strip_and_set_version() {
  if [[ -s "$strip_list" ]]; then
    work_git rm -r -q --ignore-unmatch --pathspec-from-file="$strip_list"
  fi
  set_version_keywords "$version_file" "$@"
  add_all
}

work_git reset -q --hard "$base"
work_git checkout -q -B upstream-base
strip_and_set_version "$(read_version_keyword git_refnames <<<"$ours_version")" \
  "$(read_version_keyword git_full <<<"$ours_version")" "$(read_version_keyword git_date <<<"$ours_version")"
work_git commit -q --allow-empty --no-verify -m "upstream base without the files that our copy does not have"

work_git checkout -q -B jetbrains
work_git read-tree --reset -u "$ours_tree"
work_git commit -q --allow-empty --no-verify -m "JetBrains copy of debugpy"

work_git checkout -q -f --detach "$new"
new_tag="$(work_git describe --tags --exact-match "$new" 2>/dev/null || true)"
strip_and_set_version "${new_tag:+ (tag: $new_tag)}" "$new" "$(work_git log -1 --format=%ci "$new")"
work_git branch -f upstream-new "$(work_git commit-tree "$(work_git write-tree)" -p upstream-base \
  -m "upstream target without the files that our copy does not have")"
work_git checkout -q -f jetbrains

# 3. Merge, and write the result into this directory.

echo "Merging debugpy $target into $HELPER_DIR"
status=0
output="$(work_git merge-tree --write-tree --messages jetbrains upstream-new)" || status=$?
[[ $status -le 1 ]] || die "git merge-tree failed: $output"
merged_tree="$(sed -n 1p <<<"$output")"

repo_git ls-files -z -- "$HELPER_DIR" "$NOT_SCRIPT" | (cd "$REPO_ROOT" && xargs -0 rm -f --)
work_git archive --format=tar "$merged_tree:$UPSTREAM_DIR" | tar -x -f - -C "$REPO_ROOT/$HELPER_DIR" --exclude "$SCRIPT_NAME"
repo_git add -A -- "$HELPER_DIR" "$NOT_SCRIPT"

if [[ $status -eq 0 ]]; then
  echo "The merge has no conflicts. The changes are staged and not committed."
  exit 0
fi

# Put the base, ours and theirs stages of each conflicted file into the index.
conflicts="$(sed -n '2,/^$/p' <<<"$output" | sed '/^$/d')"
{
  cut -f 2- <<<"$conflicts" | sort -u | while IFS= read -r path; do
    printf '0 %040d\t%s\n' 0 "$HELPER_DIR/${path#"$UPSTREAM_DIR"/}"
  done
  while IFS=$' \t' read -r mode oid stage path; do
    work_git cat-file blob "$oid" | repo_git hash-object -w --stdin >/dev/null
    printf '%s %s %s\t%s\n' "$mode" "$oid" "$stage" "$HELPER_DIR/${path#"$UPSTREAM_DIR"/}"
  done <<<"$conflicts"
} | repo_git update-index --index-info

echo
sed -n '/^$/,$p' <<<"$output" | sed '/^$/d' | grep '^CONFLICT' || true
echo
echo "The merge has conflicts. Resolve them manually and mark them as resolved with \"git add <file>\"."
echo "The other changes are staged and not committed."
exit 1
