"""Utility functions and classes used by nose internally.
"""
import inspect
import os
import sys
import types
try:
    # for python 3
    from types import ClassType, TypeType
    class_types = (ClassType, TypeType)
except:
    class_types = (type, )

try:
    #for jython
    from compiler.consts import CO_GENERATOR
except:
    CO_GENERATOR=0x20

PYTHON_VERSION_MAJOR = sys.version_info[0]
PYTHON_VERSION_MINOR = sys.version_info[1]

def cmp_lineno(a, b):
    """Compare functions by their line numbers.
    """
    return cmp(func_lineno(a), func_lineno(b))

def func_lineno(func):
    """Get the line number of a function.
    """
    try:
        return func.compat_co_firstlineno
    except AttributeError:
        try:
            if PYTHON_VERSION_MAJOR == 3:
                return func.__code__.co_firstlineno
            return func.func_code.co_firstlineno
        except AttributeError:
            return -1

def isclass(obj):
    obj_type = type(obj)
    return obj_type in class_types or issubclass(obj_type, type)

def isgenerator(func):
    if PYTHON_VERSION_MAJOR == 3:
        return inspect.isgeneratorfunction(func)
    try:
        return func.func_code.co_flags & CO_GENERATOR != 0
    except AttributeError:
        return False

def resolve_name(name, module=None):
    """Resolve a dotted name to a module and its parts.
    """
    parts = name.split('.')
    parts_copy = parts[:]
    if module is None:
        while parts_copy:
            try:
                module = __import__('.'.join(parts_copy))
                break
            except ImportError:
                del parts_copy[-1]
                if not parts_copy:
                    raise
        parts = parts[1:]
    obj = module
    for part in parts:
        obj = getattr(obj, part)
    return obj

def try_run(obj, names):
    """Given a list of possible method names, try to run them with the
    provided object.
    """
    for name in names:
        func = getattr(obj, name, None)
        if func is not None:
            if type(obj) == types.ModuleType:
                try:
                    args, varargs, varkw, defaults = inspect.getargspec(func)
                except TypeError:
                    if hasattr(func, '__call__'):
                        func = func.__call__
                    try:
                        args, varargs, varkw, defaults = \
                            inspect.getargspec(func)
                        args.pop(0)
                    except TypeError:
                        raise TypeError("Attribute %s of %r is not a python "
                                        "function. Only functions or callables"
                                        " may be used as fixtures." %
                                        (name, obj))
                if len(args):
                    return func(obj)
            return func()

def src(filename):
    """Find the python source file for a .pyc, .pyo
    or $py.class file on jython
    """
    if filename is None:
        return filename
    if sys.platform.startswith('java') and filename.endswith('$py.class'):
        return '.'.join((filename[:-9], 'py'))
    base, ext = os.path.splitext(filename)
    if ext in ('.pyc', '.pyo', '.py'):
        return '.'.join((base, 'py'))
    return filename

def transplant_class(cls, module):
    """
    Make a class appear to reside in `module`, rather than the module in which
    it is actually defined.
    """
    class C(cls):
        pass
    C.__module__ = module
    C.__name__ = cls.__name__
    return C

def add_path(path, config=None):
    """Ensure that the path, or the root of the current package (if
    path is in a package), is in sys.path.
    """
    if not path:
        return []
    added = []
    parent = os.path.dirname(path)
    if (parent
        and os.path.exists(os.path.join(path, '__init__.py'))):
        added.extend(add_path(parent, config))
    elif not path in sys.path:
        sys.path.insert(0, path)
        added.append(path)
    if config and config.srcDirs:
        for dirname in config.srcDirs:
            dirpath = os.path.join(path, dirname)
            if os.path.isdir(dirpath):
                sys.path.insert(0, dirpath)
                added.append(dirpath)
    return added

def transplant_func(func, module = None):
    """
    Make a function imported from module A appear as if it is located
    in module B.
    """

    def newfunc(*arg, **kw):
        return func(*arg, **kw)

    newfunc = make_decorator(func)(newfunc)
    if module is None:
        newfunc.__module__ = inspect.getmodule(func)
    else:
        newfunc.__module__ = module
    return newfunc

def make_decorator(func):
    """
    Wraps a test decorator so as to properly replicate metadata
    of the decorated function.
    """
    def decorate(newfunc):
        if hasattr(func, 'compat_func_name'):
            name = func.compat_func_name
        else:
            name = func.__name__
        newfunc.__dict__ = func.__dict__
        newfunc.__doc__ = func.__doc__
        if not hasattr(newfunc, 'compat_co_firstlineno'):
            if PYTHON_VERSION_MAJOR == 3:
                newfunc.compat_co_firstlineno = func.__code__.co_firstlineno
            else:
                newfunc.compat_co_firstlineno = func.func_code.co_firstlineno
        try:
            newfunc.__name__ = name
        except TypeError:
            newfunc.compat_func_name = name
        return newfunc
    return decorate
