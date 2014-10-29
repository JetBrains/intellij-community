
import sys
from pydevd_constants import IS_PY24, IS_PY3K
from pydevd_constants import DictContains
from types import ModuleType


class ImportHookManager(ModuleType):
    def __init__(self, name, system_import):
        ModuleType.__init__(self, name)
        self._system_import = system_import
        self._modules_to_patch = {}

    def add_module_name(self, module_name, activate_function):
        self._modules_to_patch[module_name] = activate_function

    def do_import(self, name, globals=None, locals=None, fromlist=None, level=-2):
        if level == -2:
            # fake impossible value; default value depends on version
            if IS_PY24:
                # the level parameter was added in version 2.5
                return self._system_import(name, globals, locals, fromlist)
            elif IS_PY3K:
                # default value for level parameter in python 3
                level = 0
            else:
                # default value for level parameter in other versions
                level = -1
        module = self._system_import(name, globals, locals, fromlist, level)
        if DictContains(self._modules_to_patch, name):
            self._modules_to_patch[name]() #call activate function
            self._modules_to_patch.pop(name)
        return module

try:
    import __builtin__ as builtins
except ImportError:
    import builtins

import_hook_manager = ImportHookManager(__name__ + '.import_hook', builtins.__import__)
builtins.__import__ = import_hook_manager.do_import
sys.modules[import_hook_manager.__name__] = import_hook_manager
del builtins