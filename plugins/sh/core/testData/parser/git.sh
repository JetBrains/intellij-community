for repo in $REPOS; do
  echo "*** GIT: $repo"
  FOLDER_PREFIX="$repo/" AUTHORS_REWRITE_TABLE_FILE="$ROOT/authors-rewrite-table" git clone --mirror hg::$(pwd)/hg/$repo git/$repo
  pushd git/$repo
  git remote remove origin
  # rename local branches "branches/*__closed__*" -> tags
  git branch --list 'branches/*__closed__*' | cut -c12- | while IFS='' read -r branchname; do
    git tag "$branchname" "branches/$branchname"
    git branch -D "branches/$branchname"
  done
  # rename local branches "branches/*__head__*" -> tags
  git branch --list 'branches/*__head__*' | cut -c12- | while IFS='' read -r branchname; do
    git tag "$branchname" "branches/$branchname"
    git branch -D "branches/$branchname"
  done
  # rename local branches "branches/XXX" -> "XXX"
  git branch --list 'branches/*' | cut -c12- | while IFS='' read -r branchname; do
    git branch "$branchname" "branches/$branchname"
    git branch -D "branches/$branchname"
  done
  echo "Available branches:"
  git branch

  git branch | cut -b3- >>../all-branches-non-unique
  git tag >>../all-tags-non-unique

  popd
done