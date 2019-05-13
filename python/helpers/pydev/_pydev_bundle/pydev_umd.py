"""
The UserModuleDeleter and runfile methods are copied from
Spyder and carry their own license agreement.
http://code.google.com/p/spyderlib/source/browse/spyderlib/widgets/externalshell/sitecustomize.py

Spyder License Agreement (MIT License)
--------------------------------------

Copyright (c) 2009-2012 Pierre Raybaut

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
"""

import os
import sys

from _pydev_bundle import pydev_imports
from pydevd_file_utils import get_fullname, rPath
from _pydevd_bundle.pydevd_utils import save_main_module


# The following classes and functions are mainly intended to be used from
# an interactive Python session
class UserModuleDeleter:
    """
    User Module Deleter (UMD) aims at deleting user modules
    to force Python to deeply reload them during import

    pathlist [list]: blacklist in terms of module path
    namelist [list]: blacklist in terms of module name
    """
    def __init__(self, namelist=None, pathlist=None):
        if namelist is None:
            namelist = []
        self.namelist = namelist
        if pathlist is None:
            pathlist = []
        self.pathlist = pathlist
        try:
            # blacklist all files in org.python.pydev/pysrc
            import pydev_pysrc, inspect
            self.pathlist.append(os.path.dirname(pydev_pysrc.__file__))
        except:
            pass
        self.previous_modules = list(sys.modules.keys())

    def is_module_blacklisted(self, modname, modpath):
        for path in [sys.prefix] + self.pathlist:
            if modpath.startswith(path):
                return True
        else:
            return set(modname.split('.')) & set(self.namelist)

    def run(self, verbose=False):
        """
        Del user modules to force Python to deeply reload them

        Do not del modules which are considered as system modules, i.e.
        modules installed in subdirectories of Python interpreter's binary
        Do not del C modules
        """
        log = []
        modules_copy = dict(sys.modules)
        for modname, module in modules_copy.items():
            if modname == 'aaaaa':
                print(modname, module)
                print(self.previous_modules)
            if modname not in self.previous_modules:
                modpath = getattr(module, '__file__', None)
                if modpath is None:
                    # *module* is a C module that is statically linked into the
                    # interpreter. There is no way to know its path, so we
                    # choose to ignore it.
                    continue
                if not self.is_module_blacklisted(modname, modpath):
                    log.append(modname)
                    del sys.modules[modname]
        if verbose and log:
            print("\x1b[4;33m%s\x1b[24m%s\x1b[0m" % ("UMD has deleted",
                                                     ": " + ", ".join(log)))

__umd__ = None

_get_globals_callback = None


def _set_globals_function(get_globals):
    global _get_globals_callback
    _get_globals_callback = get_globals


def _get_interpreter_globals():
    """Return current Python interpreter globals namespace"""
    if _get_globals_callback is not None:
        return _get_globals_callback()
    else:
        try:
            from __main__ import __dict__ as namespace
        except ImportError:
            try:
                # The import fails on IronPython
                import __main__
                namespace = __main__.__dict__
            except:
                namespace
        shell = namespace.get('__ipythonshell__')
        if shell is not None and hasattr(shell, 'user_ns'):
            # IPython 0.12+ kernel
            return shell.user_ns
        else:
            # Python interpreter
            return namespace


def runfile(filename, args=None, wdir=None, is_module=False, global_vars=None):
    """
    Run filename
    args: command line arguments (string)
    wdir: working directory
    """
    try:
        if hasattr(filename, 'decode'):
            filename = filename.decode('utf-8')
    except (UnicodeError, TypeError):
        pass

    global __umd__
    if os.environ.get("PYDEV_UMD_ENABLED", "").lower() == "true":
        if __umd__ is None:
            namelist = os.environ.get("PYDEV_UMD_NAMELIST", None)
            if namelist is not None:
                namelist = namelist.split(',')
            __umd__ = UserModuleDeleter(namelist=namelist)
        else:
            verbose = os.environ.get("PYDEV_UMD_VERBOSE", "").lower() == "true"
            __umd__.run(verbose=verbose)

    if global_vars is None:
        m = save_main_module(filename, 'pydev_umd')
        global_vars = m.__dict__
        try:
            global_vars['__builtins__'] = __builtins__
        except NameError:
            pass  # Not there on Jython...

    local_vars = global_vars

    module_name = None
    entry_point_fn = None
    if is_module:
        file, _, entry_point_fn = filename.partition(':')
        module_name = file
        filename = get_fullname(file)
        if filename is None:
            sys.stderr.write("No module named %s\n" % file)
            return
    else:
        # The file being run must be in the pythonpath (even if it was not before)
        sys.path.insert(0, os.path.split(rPath(filename))[0])

    global_vars['__file__'] = filename

    sys.argv = [filename]
    if args is not None:
        for arg in args:
            sys.argv.append(arg)

    if wdir is not None:
        try:
            if hasattr(wdir, 'decode'):
                wdir = wdir.decode('utf-8')
        except (UnicodeError, TypeError):
            pass
        os.chdir(wdir)

    try:
        if not is_module:
            pydev_imports.execfile(filename, global_vars, local_vars)  # execute the script
        else:
            # treat ':' as a seperator between module and entry point function
            # if there is no entry point we run we same as with -m switch. Otherwise we perform
            # an import and execute the entry point
            if entry_point_fn:
                mod = __import__(module_name, level=0, fromlist=[entry_point_fn], globals=global_vars, locals=local_vars)
                func = getattr(mod, entry_point_fn)
                func()
            else:
                # Run with the -m switch
                import runpy
                if hasattr(runpy, '_run_module_as_main'):
                    runpy._run_module_as_main(module_name)
                else:
                    runpy.run_module(module_name)
    finally:
        sys.argv = ['']
        interpreter_globals = _get_interpreter_globals()
        interpreter_globals.update(global_vars)
