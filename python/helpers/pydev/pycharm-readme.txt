The sources in this folder are PyCharm version of the debugger PyDev.Debugger.
They should be in sync, so after testing all commits should be back-ported to the original repo.

How to do it?

a) You should have PyDev.Debugger repo clone and fetch there IDEA commits related to this folder with changed paths from python/pydev/* to *
b) Then cherry-pick all necessary commits resolving merge conflicts

Steps:

1) git clone https://github.com/fabioz/PyDev.Debugger.git
2) <clone IDEA Community repo>
and in this folder:
3) git remote rm origin
4) git tag -l | xargs git tag -d
5) git filter-branch --subdirectory-filter python/helpers/pydev --prune-empty --index-filter 'SHA=$(git write-tree); rm $GIT_INDEX_FILE && git read-tree --prefix= $SHA' -- --all
in PyDev.Debugger folder:
6) git remote add temp-repo <path/to/idea/folder>
7) git fetch temp-repo
8) Cherry-pick all the changes