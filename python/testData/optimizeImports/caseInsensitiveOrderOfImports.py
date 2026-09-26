import mod_a
import mod_Z
# noinspection PyUnusedImports
import pkg.pkg_a
# noinspection PyUnusedImports
import pkg.pkg_Z
from mod import var_a
from mod import var_Z
from pkg.pkg_a import mod1
from pkg.pkg_Z import mod2

print(mod_a, mod_Z, pkg.pkg_a, pkg.pkg_Z, mod1, mod2, var_a, var_Z)