# Emulates how symbols are exported from submodules in numpy.core.numeric

from . import submod

__all__ = ['foo']

foo = 'foo'


def extend_all(module):
    existing = set(__all__)
    mall = getattr(module, '__all__')
    for a in mall:
        if a not in existing:
            __all__.append(a)


from .submod import *

extend_all(submod)
