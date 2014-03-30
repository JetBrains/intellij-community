"""
Copied from the python xreload (available for change)

Alternative to reload().

This works by executing the module in a scratch namespace, and then
patching classes, methods and functions in place.  This avoids the
need to patch instances.  New objects are copied into the target
namespace.

Some of the many limitations include:

- Global mutable objects other than classes are simply replaced, not patched

- Code using metaclasses is not handled correctly

- Code creating global singletons is not handled correctly

- Functions and methods using decorators (other than classmethod and
  staticmethod) is not handled correctly

- Renamings are not handled correctly

- Dependent modules are not reloaded

- When a dependent module contains 'from foo import bar', and
  reloading foo deletes foo.bar, the dependent module continues to use
  the old foo.bar object rather than failing

- Frozen modules and modules loaded from zip files aren't handled
  correctly

- Classes involving __slots__ are not handled correctly
"""

import imp
import sys
import types


def xreload(mod):
    """Reload a module in place, updating classes, methods and functions.

    Args:
      mod: a module object

    Returns:
      The (updated) input object itself.
    """
    # Get the module name, e.g. 'foo.bar.whatever'
    modname = mod.__name__
    # Get the module namespace (dict) early; this is part of the type check
    modns = mod.__dict__
    # Parse it into package name and module name, e.g. 'foo.bar' and 'whatever'
    i = modname.rfind(".")
    if i >= 0:
        pkgname, modname = modname[:i], modname[i+1:]
    else:
        pkgname = None
    # Compute the search path
    if pkgname:
        # We're not reloading the package, only the module in it
        pkg = sys.modules[pkgname]
        path = pkg.__path__  # Search inside the package
    else:
        # Search the top-level module path
        pkg  = None
        path = None  # Make find_module() uses the default search path
    # Find the module; may raise ImportError
    (stream, filename, (suffix, mode, kind)) = imp.find_module(modname, path)
    # Turn it into a code object
    try:
        # Is it Python source code or byte code read from a file?
        if kind not in (imp.PY_COMPILED, imp.PY_SOURCE):
            # Fall back to built-in reload()
            return reload(mod)
        if kind == imp.PY_SOURCE:
            source = stream.read()
            code = compile(source, filename, "exec")
        else:
            import marshal
            code = marshal.load(stream)
    finally:
        if stream:
            stream.close()
    # Execute the code.  We copy the module dict to a temporary; then
    # clear the module dict; then execute the new code in the module
    # dict; then swap things back and around.  This trick (due to
    # Glyph Lefkowitz) ensures that the (readonly) __globals__
    # attribute of methods and functions is set to the correct dict
    # object.
    tmpns = modns.copy()
    modns.clear()
    modns["__name__"] = tmpns["__name__"]
    exec(code, modns)
    # Now we get to the hard part
    oldnames = set(tmpns)
    newnames = set(modns)
    # Update attributes in place
    for name in oldnames & newnames:
        modns[name] = _update(tmpns[name], modns[name])
    # Done!
    return mod


def _update(oldobj, newobj):
    """Update oldobj, if possible in place, with newobj.

    If oldobj is immutable, this simply returns newobj.

    Args:
      oldobj: the object to be updated
      newobj: the object used as the source for the update

    Returns:
      either oldobj, updated in place, or newobj.
    """
    if oldobj is newobj:
        # Probably something imported
        return newobj
    if type(oldobj) is not type(newobj):
        # Cop-out: if the type changed, give up
        return newobj
    if hasattr(newobj, "__reload_update__"):
        # Provide a hook for updating
        return newobj.__reload_update__(oldobj)
    
    if hasattr(types, 'ClassType'):
        classtype = types.ClassType
    else:
        classtype = type
    
    if isinstance(newobj, classtype):
        return _update_class(oldobj, newobj)
    if isinstance(newobj, types.FunctionType):
        return _update_function(oldobj, newobj)
    if isinstance(newobj, types.MethodType):
        return _update_method(oldobj, newobj)
    if isinstance(newobj, classmethod):
        return _update_classmethod(oldobj, newobj)
    if isinstance(newobj, staticmethod):
        return _update_staticmethod(oldobj, newobj)
    # Not something we recognize, just give up
    return newobj


# All of the following functions have the same signature as _update()


def _update_function(oldfunc, newfunc):
    """Update a function object."""
    oldfunc.__doc__ = newfunc.__doc__
    oldfunc.__dict__.update(newfunc.__dict__)
    
    try:
        oldfunc.__code__ = newfunc.__code__
    except AttributeError:
        oldfunc.func_code = newfunc.func_code
    try:
        oldfunc.__defaults__ = newfunc.__defaults__
    except AttributeError:
        oldfunc.func_defaults = newfunc.func_defaults
        
    return oldfunc


def _update_method(oldmeth, newmeth):
    """Update a method object."""
    # XXX What if im_func is not a function?
    _update(oldmeth.im_func, newmeth.im_func)
    return oldmeth


def _update_class(oldclass, newclass):
    """Update a class object."""
    olddict = oldclass.__dict__
    newdict = newclass.__dict__
    oldnames = set(olddict)
    newnames = set(newdict)
    for name in newnames - oldnames:
        setattr(oldclass, name, newdict[name])
    for name in oldnames - newnames:
        delattr(oldclass, name)
    for name in oldnames & newnames - set(['__dict__', '__doc__']):
        setattr(oldclass, name,  _update(olddict[name], newdict[name]))
    return oldclass


def _update_classmethod(oldcm, newcm):
    """Update a classmethod update."""
    # While we can't modify the classmethod object itself (it has no
    # mutable attributes), we *can* extract the underlying function
    # (by calling __get__(), which returns a method object) and update
    # it in-place.  We don't have the class available to pass to
    # __get__() but any object except None will do.
    _update(oldcm.__get__(0), newcm.__get__(0))
    return newcm


def _update_staticmethod(oldsm, newsm):
    """Update a staticmethod update."""
    # While we can't modify the staticmethod object itself (it has no
    # mutable attributes), we *can* extract the underlying function
    # (by calling __get__(), which returns it) and update it in-place.
    # We don't have the class available to pass to __get__() but any
    # object except None will do.
    _update(oldsm.__get__(0), newsm.__get__(0))
    return newsm
