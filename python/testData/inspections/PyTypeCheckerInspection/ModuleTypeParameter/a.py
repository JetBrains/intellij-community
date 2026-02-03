import module
from types import ModuleType

def foo(m: ModuleType):
    pass

def bar(m):
    return m.__name__

foo(module)
bar(module)