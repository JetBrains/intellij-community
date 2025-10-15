from .m1 import foo
from .m1 import <error descr="Cannot find reference 'bar' in 'package_with_stub_in_path.m1'">bar</error>

foo2 = foo
bar2 = bar