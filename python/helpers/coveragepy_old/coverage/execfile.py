# Licensed under the Apache License: http://www.apache.org/licenses/LICENSE-2.0
# For details: https://github.com/nedbat/coveragepy/blob/master/NOTICE.txt

"""Execute files of Python code."""

import inspect
import marshal
import os
import struct
import sys
import types

from coverage import env
from coverage.backward import BUILTINS
from coverage.backward import PYC_MAGIC_NUMBER, imp, importlib_util_find_spec
from coverage.files import canonical_filename, python_reported_file
from coverage.misc import CoverageException, ExceptionDuringRun, NoCode, NoSource, isolate_module
from coverage.phystokens import compile_unicode
from coverage.python import get_python_source

os = isolate_module(os)


class DummyLoader(object):
    """A shim for the pep302 __loader__, emulating pkgutil.ImpLoader.

    Currently only implements the .fullname attribute
    """
    def __init__(self, fullname, *_args):
        self.fullname = fullname


if importlib_util_find_spec:
    def find_module(modulename):
        """Find the module named `modulename`.

        Returns the file path of the module, the name of the enclosing
        package, and the spec.
        """
        try:
            spec = importlib_util_find_spec(modulename)
        except ImportError as err:
            raise NoSource(str(err))
        if not spec:
            raise NoSource("No module named %r" % (modulename,))
        pathname = spec.origin
        packagename = spec.name
        if spec.submodule_search_locations:
            mod_main = modulename + ".__main__"
            spec = importlib_util_find_spec(mod_main)
            if not spec:
                raise NoSource(
                    "No module named %s; "
                    "%r is a package and cannot be directly executed"
                    % (mod_main, modulename)
                )
            pathname = spec.origin
            packagename = spec.name
        packagename = packagename.rpartition(".")[0]
        return pathname, packagename, spec
else:
    def find_module(modulename):
        """Find the module named `modulename`.

        Returns the file path of the module, the name of the enclosing
        package, and None (where a spec would have been).
        """
        openfile = None
        glo, loc = globals(), locals()
        try:
            # Search for the module - inside its parent package, if any - using
            # standard import mechanics.
            if '.' in modulename:
                packagename, name = modulename.rsplit('.', 1)
                package = __import__(packagename, glo, loc, ['__path__'])
                searchpath = package.__path__
            else:
                packagename, name = None, modulename
                searchpath = None  # "top-level search" in imp.find_module()
            openfile, pathname, _ = imp.find_module(name, searchpath)

            # Complain if this is a magic non-file module.
            if openfile is None and pathname is None:
                raise NoSource(
                    "module does not live in a file: %r" % modulename
                    )

            # If `modulename` is actually a package, not a mere module, then we
            # pretend to be Python 2.7 and try running its __main__.py script.
            if openfile is None:
                packagename = modulename
                name = '__main__'
                package = __import__(packagename, glo, loc, ['__path__'])
                searchpath = package.__path__
                openfile, pathname, _ = imp.find_module(name, searchpath)
        except ImportError as err:
            raise NoSource(str(err))
        finally:
            if openfile:
                openfile.close()

        return pathname, packagename, None


class PyRunner(object):
    """Multi-stage execution of Python code.

    This is meant to emulate real Python execution as closely as possible.

    """
    def __init__(self, args, as_module=False):
        self.args = args
        self.as_module = as_module

        self.arg0 = args[0]
        self.package = self.modulename = self.pathname = self.loader = self.spec = None

    def prepare(self):
        """Set sys.path properly.

        This needs to happen before any importing, and without importing anything.
        """
        if self.as_module:
            if env.PYBEHAVIOR.actual_syspath0_dash_m:
                path0 = os.getcwd()
            else:
                path0 = ""
        elif os.path.isdir(self.arg0):
            # Running a directory means running the __main__.py file in that
            # directory.
            path0 = self.arg0
        else:
            path0 = os.path.abspath(os.path.dirname(self.arg0))

        if os.path.isdir(sys.path[0]):
            # sys.path fakery.  If we are being run as a command, then sys.path[0]
            # is the directory of the "coverage" script.  If this is so, replace
            # sys.path[0] with the directory of the file we're running, or the
            # current directory when running modules.  If it isn't so, then we
            # don't know what's going on, and just leave it alone.
            top_file = inspect.stack()[-1][0].f_code.co_filename
            sys_path_0_abs = os.path.abspath(sys.path[0])
            top_file_dir_abs = os.path.abspath(os.path.dirname(top_file))
            sys_path_0_abs = canonical_filename(sys_path_0_abs)
            top_file_dir_abs = canonical_filename(top_file_dir_abs)
            if sys_path_0_abs != top_file_dir_abs:
                path0 = None

        else:
            # sys.path[0] is a file. Is the next entry the directory containing
            # that file?
            if sys.path[1] == os.path.dirname(sys.path[0]):
                # Can it be right to always remove that?
                del sys.path[1]

        if path0 is not None:
            sys.path[0] = python_reported_file(path0)

    def _prepare2(self):
        """Do more preparation to run Python code.

        Includes finding the module to run and adjusting sys.argv[0].
        This method is allowed to import code.

        """
        if self.as_module:
            self.modulename = self.arg0
            pathname, self.package, self.spec = find_module(self.modulename)
            if self.spec is not None:
                self.modulename = self.spec.name
            self.loader = DummyLoader(self.modulename)
            self.pathname = os.path.abspath(pathname)
            self.args[0] = self.arg0 = self.pathname
        elif os.path.isdir(self.arg0):
            # Running a directory means running the __main__.py file in that
            # directory.
            for ext in [".py", ".pyc", ".pyo"]:
                try_filename = os.path.join(self.arg0, "__main__" + ext)
                if os.path.exists(try_filename):
                    self.arg0 = try_filename
                    break
            else:
                raise NoSource("Can't find '__main__' module in '%s'" % self.arg0)

            if env.PY2:
                self.arg0 = os.path.abspath(self.arg0)

            # Make a spec. I don't know if this is the right way to do it.
            try:
                import importlib.machinery
            except ImportError:
                pass
            else:
                try_filename = python_reported_file(try_filename)
                self.spec = importlib.machinery.ModuleSpec("__main__", None, origin=try_filename)
                self.spec.has_location = True
            self.package = ""
            self.loader = DummyLoader("__main__")
        else:
            if env.PY3:
                self.loader = DummyLoader("__main__")

        self.arg0 = python_reported_file(self.arg0)

    def run(self):
        """Run the Python code!"""

        self._prepare2()

        # Create a module to serve as __main__
        main_mod = types.ModuleType('__main__')

        from_pyc = self.arg0.endswith((".pyc", ".pyo"))
        main_mod.__file__ = self.arg0
        if from_pyc:
            main_mod.__file__ = main_mod.__file__[:-1]
        if self.package is not None:
            main_mod.__package__ = self.package
        main_mod.__loader__ = self.loader
        if self.spec is not None:
            main_mod.__spec__ = self.spec

        main_mod.__builtins__ = BUILTINS

        sys.modules['__main__'] = main_mod

        # Set sys.argv properly.
        sys.argv = self.args

        try:
            # Make a code object somehow.
            if from_pyc:
                code = make_code_from_pyc(self.arg0)
            else:
                code = make_code_from_py(self.arg0)
        except CoverageException:
            raise
        except Exception as exc:
            msg = "Couldn't run '{filename}' as Python code: {exc.__class__.__name__}: {exc}"
            raise CoverageException(msg.format(filename=self.arg0, exc=exc))

        # Execute the code object.
        # Return to the original directory in case the test code exits in
        # a non-existent directory.
        cwd = os.getcwd()
        try:
            exec(code, main_mod.__dict__)
        except SystemExit:                          # pylint: disable=try-except-raise
            # The user called sys.exit().  Just pass it along to the upper
            # layers, where it will be handled.
            raise
        except Exception:
            # Something went wrong while executing the user code.
            # Get the exc_info, and pack them into an exception that we can
            # throw up to the outer loop.  We peel one layer off the traceback
            # so that the coverage.py code doesn't appear in the final printed
            # traceback.
            typ, err, tb = sys.exc_info()

            # PyPy3 weirdness.  If I don't access __context__, then somehow it
            # is non-None when the exception is reported at the upper layer,
            # and a nested exception is shown to the user.  This getattr fixes
            # it somehow? https://bitbucket.org/pypy/pypy/issue/1903
            getattr(err, '__context__', None)

            # Call the excepthook.
            try:
                if hasattr(err, "__traceback__"):
                    err.__traceback__ = err.__traceback__.tb_next
                sys.excepthook(typ, err, tb.tb_next)
            except SystemExit:                      # pylint: disable=try-except-raise
                raise
            except Exception:
                # Getting the output right in the case of excepthook
                # shenanigans is kind of involved.
                sys.stderr.write("Error in sys.excepthook:\n")
                typ2, err2, tb2 = sys.exc_info()
                err2.__suppress_context__ = True
                if hasattr(err2, "__traceback__"):
                    err2.__traceback__ = err2.__traceback__.tb_next
                sys.__excepthook__(typ2, err2, tb2.tb_next)
                sys.stderr.write("\nOriginal exception was:\n")
                raise ExceptionDuringRun(typ, err, tb.tb_next)
            else:
                sys.exit(1)
        finally:
            os.chdir(cwd)


def run_python_module(args):
    """Run a Python module, as though with ``python -m name args...``.

    `args` is the argument array to present as sys.argv, including the first
    element naming the module being executed.

    This is a helper for tests, to encapsulate how to use PyRunner.

    """
    runner = PyRunner(args, as_module=True)
    runner.prepare()
    runner.run()


def run_python_file(args):
    """Run a Python file as if it were the main program on the command line.

    `args` is the argument array to present as sys.argv, including the first
    element naming the file being executed.  `package` is the name of the
    enclosing package, if any.

    This is a helper for tests, to encapsulate how to use PyRunner.

    """
    runner = PyRunner(args, as_module=False)
    runner.prepare()
    runner.run()


def make_code_from_py(filename):
    """Get source from `filename` and make a code object of it."""
    # Open the source file.
    try:
        source = get_python_source(filename)
    except (IOError, NoSource):
        raise NoSource("No file to run: '%s'" % filename)

    code = compile_unicode(source, filename, "exec")
    return code


def make_code_from_pyc(filename):
    """Get a code object from a .pyc file."""
    try:
        fpyc = open(filename, "rb")
    except IOError:
        raise NoCode("No file to run: '%s'" % filename)

    with fpyc:
        # First four bytes are a version-specific magic number.  It has to
        # match or we won't run the file.
        magic = fpyc.read(4)
        if magic != PYC_MAGIC_NUMBER:
            raise NoCode("Bad magic number in .pyc file: {} != {}".format(magic, PYC_MAGIC_NUMBER))

        date_based = True
        if env.PYBEHAVIOR.hashed_pyc_pep552:
            flags = struct.unpack('<L', fpyc.read(4))[0]
            hash_based = flags & 0x01
            if hash_based:
                fpyc.read(8)    # Skip the hash.
                date_based = False
        if date_based:
            # Skip the junk in the header that we don't need.
            fpyc.read(4)            # Skip the moddate.
            if env.PYBEHAVIOR.size_in_pyc:
                # 3.3 added another long to the header (size), skip it.
                fpyc.read(4)

        # The rest of the file is the code object we want.
        code = marshal.load(fpyc)

    return code
