from pkg1 import subpkg2
from pkg1.subpkg2 import mod2
from pkg1.subpkg2.mod2 import VAR
from . import mod3

# malformed imports
from
from import
from pkg1.subpkg2 import

# absolute imports
import pkg1.subpkg2 as foo
from pkg1 import subpkg2 as bar

print(subpkg2, mod3, mod2, foo, bar, VAR)
