import sys
import traceback
from types import ModuleType


class ImportHookManager(ModuleType):
    def __init__(self, name, system_import):
        ModuleType.__init__(self, name)
        self._system_import = system_import
        self._modules_to_patch = {}
        self.inside_activation = False

    def add_module_name(self, module_name, activate_function):
        self._modules_to_patch[module_name] = activate_function

    def do_import(self, name, *args, **kwargs):
        activate_func = None
        if name in self._modules_to_patch:
            activate_func = self._modules_to_patch[name]

        module = self._system_import(name, *args, **kwargs)
        try:
            if activate_func and not self.inside_activation:
                self.inside_activation = True
                succeeded = activate_func()
                if succeeded and name in self._modules_to_patch:
                    # Remove if only it was executed correctly
                    self._modules_to_patch.pop(name)
                self.inside_activation = False
        except:
            sys.stderr.write("Matplotlib support failed\n")
            traceback.print_exc()
        return module


if sys.version_info[0] >= 3:
    import builtins  # py3
else:
    import __builtin__ as builtins

import_hook_manager = ImportHookManager(__name__ + '.import_hook', builtins.__import__)
builtins.__import__ = import_hook_manager.do_import
sys.modules[import_hook_manager.__name__] = import_hook_manager
del builtins
