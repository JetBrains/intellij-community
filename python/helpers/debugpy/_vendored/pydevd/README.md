# PyDev.Debugger


## New

Latest `3.x` version: the PyDev debugger now supports `sys.monitoring` which enables
really fast tracing on `Python 3.12` (so, if speed is an issue, make sure you upgrade).

## Important

https://github.com/fabioz/PyDev.Debugger is the main repository
for `pydevd` and the latest versions can always be used directly in:

- [PyDev for Eclipse](http://pydev.org): Enables the usage of `pydevd` in Eclipse (Open Source).

- [Python Debugger (PyDev) for VSCode](https://marketplace.visualstudio.com/items?itemName=fabioz.vscode-pydev-python-debugger): Enables
    the usage of `pydevd` in VSCode (note that while `pydevd` itself is open source, this extension is commercial 
    and helps in the development of the Open Source version. It has a free trial and can be used by acquiring a license for 
    `PyDev for VSCode` at: https://www.pydev.org/vscode/index.html).
    
    Note that the `Python Debugger (PyDev) for VSCode` may be used as a standalane extension for debugging `Python` by
    creating the proper configuration in a `launch.json` and launching it.
    
    Alternatively, [PyDev for VSCode](https://marketplace.visualstudio.com/items?itemName=fabioz.vscode-pydev)
    leverages it to offer additional features such as debugging of test cases.

## History / Support

The `PyDev Debugger` (`pydevd` for short) is a **Python debugger** which historically was created to
work with `PyDev` (in Eclipse).

Over the years (as it's open source -- EPL) it was adopted by other IDEs/companies
(so, it was integrated into PyCharm and VSCode Python through `debugpy`, which also bundles `pydevd`).

Note that although it was adopted by other IDEs (and over the years companies of other
commercial IDEs did provide backing), by far most of the work was done without any
external backing and the ongoing work on the project relies on community support.

So, if you like using it, please consider becoming a backer of the project (this is
done through the `PyDev` umbrella, so please see https://www.pydev.org/about.html
for how to contribute to the project).


## Source code / using

The sources for the PyDev.Debugger may be seen at:

https://github.com/fabioz/PyDev.Debugger

In general, the debugger backend should **NOT** be installed separately if you're using an IDE which already
bundles it (such as [PyDev for Eclipse](http://pydev.org), [Python Debugger (PyDev) for VSCode](https://marketplace.visualstudio.com/items?itemName=fabioz.vscode-pydev-python-debugger), 
PyCharm or the Microsoft Python VSCode Extension, which uses `debugpy`, which is another debug adapter bundling `pydevd` to be used in the Microsoft 
VSCode Python Extension and Visual Studio Python).

It is however available in PyPi so that it can be installed for doing remote debugging with `pip` -- so, when
debugging a process which runs in another machine, it's possible to `pip install pydevd` and in the code use
`pydevd.settrace(host="10.1.1.1")` (in PyDev) or `pydevd.settrace(host="10.1.1.1", protocol="dap")` (in PyDev for VSCode)
to connect the debugger backend to the debugger UI running in the IDE
(whereas previously the sources had to be manually copied from the IDE installation).

For instructions on how to `Remote Debug` with `PyDev`, see: https://www.pydev.org/manual_adv_remote_debugger.html

For instructions on how to `Remote Debug` with `PyDev for VSCode`, see: https://marketplace.visualstudio.com/items?itemName=fabioz.vscode-pydev-python-debugger

`pydevd` is compatible with Python 3.8 onwards and is tested both with CPython as well as PyPy.

For `Python 3.3 to 3.7` please keep using `pydevd 2.10.0`.

For `Python 2` please keep using `pydevd 2.8.0`.

Recent versions contain speedup modules using Cython, which are generated with a few changes in the regular files
to `cythonize` the files. To update and compile the cython sources (and generate some other auto-generated files),
`build_tools/build.py` should be run -- note that the resulting .pyx and .c files should be commited.
