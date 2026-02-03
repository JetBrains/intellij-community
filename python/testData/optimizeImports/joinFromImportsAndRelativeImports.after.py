from . import module1, module2
from .module import b, d
from .. import pkg1, pkg2
from ..module import a, c

print(a, b, c, d, module1, module2, pkg1, pkg2)

