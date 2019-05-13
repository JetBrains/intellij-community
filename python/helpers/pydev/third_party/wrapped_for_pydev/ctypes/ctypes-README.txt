(Note: this is a compiled distribution of ctypes, compiled for cygwin
 to allow using the cygwin conversions directly from interpreterInfo.  The tests
 have been removed to reduce the added size.  It is only used by PyDev on cygwin). 

Overview

    ctypes is a ffi (Foreign Function Interface) package for Python.

    It allows to call functions exposed from dlls/shared libraries and
    has extensive facilities to create, access and manipulate simpole
    and complicated C data types transparently from Python - in other
    words: wrap libraries in pure Python.

    ctypes runs on Windows, MacOS X, Linux, Solaris, FreeBSD.  It may
    also run on other systems, provided that libffi supports this
    platform.

    On Windows, ctypes contains (the beginning of) a COM framework
    mainly targetted to use and implement custom COM interfaces.


News

    ctypes now uses the same code base and libffi on all platforms.
    For easier installation, the libffi sources are now included in
    the source distribution - no need to find, build, and install a
    compatible libffi version.


Requirements

    ctypes 0.9 requires Python 2.3 or higher, since it makes intensive
    use of the new type system.

    ctypes uses libffi, which is copyright Red Hat, Inc.  Complete
    license see below.


Installation

    Windows

        On Windows, it is the easiest to download the executable
        installer for your Python version and execute this.

    Installation from source

        Separate source distributions are available for windows and
        non-windows systems.  Please use the .zip file for Windows (it
        contains the ctypes.com framework), and use the .tar.gz file
        for non-Windows systems (it contains the complete
        cross-platform libffi sources).

        To install ctypes from source, unpack the distribution, enter
        the ctypes-0.9.x source directory, and enter

            python setup.py build

	This will build the Python extension modules.  A C compiler is
	required. On OS X, the segment attribute live_support must be
	defined. If your compiler doesn't know about it, upgrade or
	set the environment variable CCASFLAGS="-Dno_live_support".

	To run the supplied tests, enter

	    python setup.py test

	To install ctypes, enter

            python setup.py install --help

        to see the avaibable options, and finally

	    python setup.py install [options]


        For Windows CE, a project file is provided in
        wince\_ctypes.vcw.  MS embedded Visual C 4.0 is required to
        build the extension modules.


Additional notes

    Current version: 0.9.9.3

    Homepage: http://starship.python.net/crew/theller/ctypes.html


ctypes license

  Copyright (c) 2000, 2001, 2002, 2003, 2004, 2005, 2006 Thomas Heller

  Permission is hereby granted, free of charge, to any person
  obtaining a copy of this software and associated documentation files
  (the "Software"), to deal in the Software without restriction,
  including without limitation the rights to use, copy, modify, merge,
  publish, distribute, sublicense, and/or sell copies of the Software,
  and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be
  included in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
  BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
  ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  SOFTWARE.

libffi license

  libffi - Copyright (c) 1996-2003  Red Hat, Inc.

  Permission is hereby granted, free of charge, to any person
  obtaining a copy of this software and associated documentation files
  (the ``Software''), to deal in the Software without restriction,
  including without limitation the rights to use, copy, modify, merge,
  publish, distribute, sublicense, and/or sell copies of the Software,
  and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be
  included in all copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED ``AS IS'', WITHOUT WARRANTY OF ANY KIND,
  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
  NONINFRINGEMENT.  IN NO EVENT SHALL CYGNUS SOLUTIONS BE LIABLE FOR
  ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF
  CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
