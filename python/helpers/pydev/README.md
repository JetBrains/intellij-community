PyDev.Debugger
==============

Travis (Linux CI):

[![Build Status](https://travis-ci.org/fabioz/PyDev.Debugger.png)](https://travis-ci.org/fabioz/PyDev.Debugger)

Appveyor (Windows CI):

[![Build status](https://ci.appveyor.com/api/projects/status/j6vjq687brbk20ux?svg=true)](https://ci.appveyor.com/project/fabioz/pydev-debugger)

This repository contains the sources for the Debugger used in PyDev & PyCharm.

It should be compatible with Python 2.4 onwards (as well as Jython 2.2.1, IronPython and PyPy -- and any
other variant which properly supports the Python structure for debuggers -- i.e.: sys.settrace/threading.settrace).