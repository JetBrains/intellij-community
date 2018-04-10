from . import m2
import m3
import subsubpkg as foo
from . import subsubpkg as bar
from .subsubpkg import m4
from pkg import m5

print(m2, m3, m4, m5, foo, bar)
