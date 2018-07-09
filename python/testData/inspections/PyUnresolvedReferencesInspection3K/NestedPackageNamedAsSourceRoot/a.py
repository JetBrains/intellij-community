from lib1 import m1
from lib1.m1 import x
from lib1.m1 import <error descr="Cannot find reference 'something' in 'm1.py'">something</error>


print(m1, x, something)
