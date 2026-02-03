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

# trick for python 3
# The following emulates the behavior (we need) of an 'unbound method' under
# Python 3.x (namely, the ability to have a class associated with a function
# definition so that things can do stuff based on its associated class)

class UnboundMethod:
    def __init__(self, cls, func):
        self.func = func
        self.__self__ = UnboundSelf(cls)

    def address(self):
        cls = self.__self__.cls
        module = cls.__module__
        m = sys.modules[module]
        file = getattr(m, '__file__', None)
        if file is not None:
            file = os.path.abspath(file)
        return (nose.util.src(file), module, "%s.%s" % (cls.__name__, self.func.__name__))

    def __call__(self, *args, **kwargs):
        return self.func(*args, **kwargs)

    def __getattr__(self, attr):
        return getattr(self.func, attr)

class UnboundSelf:
    def __init__(self, cls):
        self.cls = cls

    # We have to do this hackery because Python won't let us override the
    # __class__ attribute...
    def __getattribute__(self, attr):
        if attr == '__class__':
            return self.cls
        else:
            return object.__getattribute__(self, attr)

def unbound_method(cls, func):
    if inspect.ismethod(func):
        return func
    if not inspect.isfunction(func):
        raise TypeError('%s is not a function' % (repr(func),))
    return UnboundMethod(cls, func)

def ismethod(obj):
    return inspect.ismethod(obj) or isinstance(obj, UnboundMethod)

def isunboundmethod(obj):
    return (inspect.ismethod(obj) and obj.im_self is None) or isinstance(obj, UnboundMethod)
