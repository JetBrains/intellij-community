from complete import a
from complete import b
from complete import <error descr="Cannot find reference 'e' in 'complete.pyi'">e</error>

from incomplete import c
from incomplete import d

print(a, b, c, d, e)