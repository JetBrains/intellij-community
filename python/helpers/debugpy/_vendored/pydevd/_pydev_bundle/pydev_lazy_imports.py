"""
PEP 810 lazy imports. The debugger shows a lazy import and completes its module without loading it.
"""
import types

try:
    from types import LazyImportType
except ImportError:
    LazyImportType = None


def is_lazy_import(value):
    return LazyImportType is not None and isinstance(value, LazyImportType)


def getattr_keeping_lazy_imports(obj_to_complete, getattr):
    """Returns a getattr that gives the lazy imports of the module `obj_to_complete` and does not load them."""
    if LazyImportType is None or not isinstance(obj_to_complete, types.ModuleType):
        return getattr

    def getattr_keeping_lazy_imports(obj, name):
        # A getattr on the module loads its lazy import; the module dict holds the proxy.
        if obj is obj_to_complete:
            value = obj_to_complete.__dict__.get(name)
            if is_lazy_import(value):
                return value
        return getattr(obj, name)

    return getattr_keeping_lazy_imports


def store_loaded_lazy_import(frame, name, lazy_import, value):
    """
    An evaluation loads a lazy import only in its namespace copy. Replace the lazy import also in the frame globals, as
    Python does when the program itself uses the name. Returns True when it replaces the lazy import.
    """
    if is_lazy_import(lazy_import) and value is not lazy_import and not is_lazy_import(value) and frame.f_globals.get(name) is lazy_import:
        frame.f_globals[name] = value
        return True
    return False
