from .m1 import foo
from .m1 import <error descr="Cannot find reference 'bar' in 'm1.pyi'">bar</error>

foo2 = foo
bar2 = bar