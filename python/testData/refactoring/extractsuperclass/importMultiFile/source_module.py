import shared_module
from shared_module import module_function as my_function
from shared_module import ModuleClass

class MyClass(object):
    def do_useful_stuff(self):
        i = shared_module.MODULE_CONTANT
        my_function()
        ModuleClass()