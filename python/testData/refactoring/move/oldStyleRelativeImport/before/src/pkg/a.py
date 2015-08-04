import b as alias
from subpkg import c as alias2
from .subpkg import c

print(alias, alias2, c)
