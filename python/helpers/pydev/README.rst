PyDev.Debugger
==============

The sources for the PyDev.Debugger (used in PyDev & PyCharm) may be seen at:

https://github.com/fabioz/PyDev.Debugger

In general, the debugger backend should **NOT** be installed separately if you're using an IDE which already
bundles it (such as PyDev or PyCharm).

It is however available in PyPi so that it can be installed for doing remote debugging with `pip` -- so, when
debugging a process which runs in another machine, it's possible to `pip install pydevd` and in the code use
`pydevd.settrace(host='10.1.1.1')` to connect the debugger backend to the debugger UI running in the IDE
(whereas previously the sources had to be manually copied from the IDE installation).

It should be compatible with Python 2.4 onwards (as well as Jython 2.2.1, IronPython and PyPy -- and
any other variant which properly supports the Python structure for debuggers -- i.e.: sys.settrace/threading.settrace).

Recent versions contain speedup modules using Cython, which are generated with a few changes in the regular files
to `cythonize` the files. To update and compile the cython sources (and generate some other auto-generated files),
`build_tools/build.py` should be run -- note that the resulting .pyx and .c files should be commited.

To see performance changes, see:

https://www.speedtin.com/reports/7_pydevd_cython (performance results with cython).
https://www.speedtin.com/reports/8_pydevd_pure_python (performance results without cython).

To generate a distribution with the precompiled binaries for the IDE, `build_binaries_windows.py` should be run (
note that the environments must be pre-created as specified in that file).

To generate a distribution to upload to PyPi, `python setup.py sdist bdist_wheel` should be run for each python version
which should have a wheel and afterwards `twine upload -s dist/pydevd-*` shoud be run to actually upload the contents
to PyPi.

Travis (Linux CI):

.. |travis| image:: https://travis-ci.org/fabioz/PyDev.Debugger.png
  :target: https://travis-ci.org/fabioz/PyDev.Debugger

|travis|

Appveyor (Windows CI):

.. |appveyor| image:: https://ci.appveyor.com/api/projects/status/j6vjq687brbk20ux?svg=true
  :target: https://ci.appveyor.com/project/fabioz/pydev-debugger

|appveyor|

