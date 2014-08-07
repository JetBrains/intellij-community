"""Execute files of Python code."""

import imp, marshal, os, sys

from coverage.backward import exec_code_object, open_source
from coverage.misc import ExceptionDuringRun, NoCode, NoSource


try:
    # In Py 2.x, the builtins were in __builtin__
    BUILTINS = sys.modules['__builtin__']
except KeyError:
    # In Py 3.x, they're in builtins
    BUILTINS = sys.modules['builtins']


def rsplit1(s, sep):
    """The same as s.rsplit(sep, 1), but works in 2.3"""
    parts = s.split(sep)
    return sep.join(parts[:-1]), parts[-1]


def run_python_module(modulename, args):
    """Run a python module, as though with ``python -m name args...``.

    `modulename` is the name of the module, possibly a dot-separated name.
    `args` is the argument array to present as sys.argv, including the first
    element naming the module being executed.

    """
    openfile = None
    glo, loc = globals(), locals()
    try:
        try:
            # Search for the module - inside its parent package, if any - using
            # standard import mechanics.
            if '.' in modulename:
                packagename, name = rsplit1(modulename, '.')
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
        except ImportError:
            _, err, _ = sys.exc_info()
            raise NoSource(str(err))
    finally:
        if openfile:
            openfile.close()

    # Finally, hand the file off to run_python_file for execution.
    pathname = os.path.abspath(pathname)
    args[0] = pathname
    run_python_file(pathname, args, package=packagename)


def run_python_file(filename, args, package=None):
    """Run a python file as if it were the main program on the command line.

    `filename` is the path to the file to execute, it need not be a .py file.
    `args` is the argument array to present as sys.argv, including the first
    element naming the file being executed.  `package` is the name of the
    enclosing package, if any.

    """
    # Create a module to serve as __main__
    old_main_mod = sys.modules['__main__']
    main_mod = imp.new_module('__main__')
    sys.modules['__main__'] = main_mod
    main_mod.__file__ = filename
    if package:
        main_mod.__package__ = package
    main_mod.__builtins__ = BUILTINS

    # Set sys.argv properly.
    old_argv = sys.argv
    sys.argv = args

    try:
        # Make a code object somehow.
        if filename.endswith(".pyc") or filename.endswith(".pyo"):
            code = make_code_from_pyc(filename)
        else:
            code = make_code_from_py(filename)

        # Execute the code object.
        try:
            exec_code_object(code, main_mod.__dict__)
        except SystemExit:
            # The user called sys.exit().  Just pass it along to the upper
            # layers, where it will be handled.
            raise
        except:
            # Something went wrong while executing the user code.
            # Get the exc_info, and pack them into an exception that we can
            # throw up to the outer loop.  We peel two layers off the traceback
            # so that the coverage.py code doesn't appear in the final printed
            # traceback.
            typ, err, tb = sys.exc_info()
            raise ExceptionDuringRun(typ, err, tb.tb_next.tb_next)
    finally:
        # Restore the old __main__
        sys.modules['__main__'] = old_main_mod

        # Restore the old argv and path
        sys.argv = old_argv

def make_code_from_py(filename):
    """Get source from `filename` and make a code object of it."""
    # Open the source file.
    try:
        source_file = open_source(filename)
    except IOError:
        raise NoSource("No file to run: %r" % filename)

    try:
        source = source_file.read()
    finally:
        source_file.close()

    # We have the source.  `compile` still needs the last line to be clean,
    # so make sure it is, then compile a code object from it.
    if not source or source[-1] != '\n':
        source += '\n'
    code = compile(source, filename, "exec")

    return code


def make_code_from_pyc(filename):
    """Get a code object from a .pyc file."""
    try:
        fpyc = open(filename, "rb")
    except IOError:
        raise NoCode("No file to run: %r" % filename)

    try:
        # First four bytes are a version-specific magic number.  It has to
        # match or we won't run the file.
        magic = fpyc.read(4)
        if magic != imp.get_magic():
            raise NoCode("Bad magic number in .pyc file")

        # Skip the junk in the header that we don't need.
        fpyc.read(4)            # Skip the moddate.
        if sys.version_info >= (3, 3):
            # 3.3 added another long to the header (size), skip it.
            fpyc.read(4)

        # The rest of the file is the code object we want.
        code = marshal.load(fpyc)
    finally:
        fpyc.close()

    return code
