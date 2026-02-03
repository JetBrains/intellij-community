Do not delete this class and `testSrc` / `testResources` folders.

Deletion of these files will result into regenerating BUILD.bazel files
and across whole project on top of bazel-targets.json, that will trigger recompilation of things
in Rider that haven't been compiled in a long time, causing Safe Push to constantly fail.

https://youtrack.jetbrains.com/issue/RIDER-130267
