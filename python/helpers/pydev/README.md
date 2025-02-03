# PyDev.Debugger

PyCharms' fork of [PyDev.Debugger][pydevd].

## Installation

In general, the debugger backend should **NOT** be installed separately if you're using an IDE which already
bundles it (such as PyDev or PyCharm).

## Compatibility

It is however available in PyPi so that it can be installed for doing remote debugging with `pip` -- so, when
debugging a process which runs in another machine, it's possible to `pip install pydevd-pycharm` and in the code use
`pydevd_pycharm.settrace(host='10.1.1.1')` to connect the debugger backend to the debugger UI running in the IDE
(whereas previously the sources had to be manually copied from the IDE installation).

It should be compatible with Python 2.6 onwards (as well as Jython 2.7, IronPython and PyPy -- and
any other variant which properly supports the Python structure for debuggers -- i.e.: sys.settrace/threading.settrace).

Recent versions contain speedup modules using Cython, which are generated with a few changes in the regular files
to `cythonize` the files. To update and compile the cython sources (and generate some other auto-generated files),
`build_tools/build.py` should be run -- note that the resulting .pyx and .c files should be commited.

To generate a distribution with the precompiled binaries for the IDE, `build_binaries_windows.py` should be run (
note that the environments must be pre-created as specified in that file).

To generate a distribution to upload to PyPi, `python setup.py sdist bdist_wheel` should be run for each python version
which should have a wheel and afterwards `twine upload -s dist/pydevd-*` should be run to actually upload the contents
to PyPi.

## Dependencies

CI dependencies are stored in `ci-requirements/`. These are high-level dependencies required to initialize tests execution.
Basically `tox` and it's transient requirements.

Test dependencies are stored in `test-requirements/`. These dependencies are required for successful execution of all the tests.

For local development you only need CI dependencies. Test dependencies are completely handled by `tox`, assuming you are running tests
through it.

Dependencies are pinned and split by supported Python version. It is done ...

- to avoid rogue dependency update crashing the tests and consequently safe-push overnight if the test is in the aggregator,
- to have reproducible builds,
- to avoid finding a set of dependencies which satisfy all the supported Python version simultaneously.

For more details on the current dependency declaration approach see [PCQA-914][PCQA-914] and [PCQA-904][PCQA-904].

## Tests

Tests are executed via `tox` with the help of `pytest`.

To run all tests ...

```shell
tox
```

To run test vs. a specific Python version, e.g., Python 3.13 ...

```shell
tox -e py313
```

To run a specific test vs. a specific Python version ...

```shell
tox -e py313 -- pydev_tests/test_pyserver.py::TestCPython::test_message
```

[pydevd]: https://github.com/fabioz/PyDev.Debugger

[PCQA-904]: https://youtrack.jetbrains.com/issue/PCQA-904

[PCQA-914]: https://youtrack.jetbrains.com/issue/PCQA-914