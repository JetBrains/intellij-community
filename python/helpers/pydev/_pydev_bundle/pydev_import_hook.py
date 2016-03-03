
import sys
from _pydevd_bundle.pydevd_constants import dict_contains
from types import ModuleType


class ImportHookManager(ModuleType):
    def __init__(self, name, system_import):
        ModuleType.__init__(self, name)
        self._system_import = system_import
        self._modules_to_patch = {}

    def add_module_name(self, module_name, activate_function):
        self._modules_to_patch[module_name] = activate_function

    def do_import(self, name, *args, **kwargs):
        activate_func = None
        if dict_contains(self._modules_to_patch, name):
            activate_func = self._modules_to_patch.pop(name)

        module = self._system_import(name, *args, **kwargs)
        try:
            if activate_func:
                activate_func() #call activate function
        except:
            sys.stderr.write("Matplotlib support failed\n")
        return module

try:
    import __builtin__ as builtins
except ImportError:
    import builtins

import_hook_manager = ImportHookManager(__name__ + '.import_hook', builtins.__import__)
builtins.__import__ = import_hook_manager.do_import
sys.modules[import_hook_manager.__name__] = import_hook_manager
del builtins