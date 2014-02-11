from dest_module import NewParent
import shared_module

class MyClass(shared_module.TheParentOfItAll, NewParent):
    pass