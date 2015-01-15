import sys
try:
    # Python2
    reload(sys)
except:
    # Python3
    import importlib
    importlib.reload(sys)

def try_import(module_name):
    """
    Import and return *module_name*.

    Unlike the standard try/except approach to optional imports, inspect
    the stack to avoid catching ImportErrors raised from **within** the
    module. Only return None if *module_name* itself cannot be imported.

    It is used in rapidsms, django-oscar and may be somewhere else.
    """
    try:
        __import__(module_name)
        return sys.modules[module_name]
    except ImportError:
        type, info, traceback = sys.exc_info()
        if traceback.tb_next:
            raise
        return None

result = try_import("datetime")
result = try_import("nonexistent_module")
result = None