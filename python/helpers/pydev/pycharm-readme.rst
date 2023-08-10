Synchronization with PyDev.Debugger
===================================

The sources in this folder are PyCharm version of the debugger `PyDev.Debugger`_.
They should be in sync, so after testing all commits should be
back-ported to the original repo.

.. _PyDev.Debugger: https://github.com/fabioz/PyDev.Debugger.git

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

Note about C Files
==================

PyCharm does not ship with pre-built Cython extensions for
Linux. Instead, the extensions are built on site. The mentioned C
files are:

- ``pydevd_cython.c`` from ``_pydevd_bundle``
- ``pydevd_frame_evaluator.c`` from ``_pydevd_frame_eval`` package.

The latter is version-specific. The variants for different Python versions
live in ``_pydevd_frame_eval/cython``. The compatible Cython module will be
picked up during the build process.

Cython generates C files but they **aren't saved**
during the standard extension build process initiated by calling the
``buildBinaries`` task from ``build-debug-binaries`` Gradle
project. To regenerate the C files, the following two commands
should be called from the ``pydev`` root directory:

::

   PYTHONPATH=. python3.8 build_tools/build.py --no-remove-binaries
   PYTHONPATH=. python3.10 build_tools/build.py --no-remove-binaries

Pay attention that two different versions of Python are used. Each
version corresponds to a version range from ``_pydevd_frame_eval/cython``.