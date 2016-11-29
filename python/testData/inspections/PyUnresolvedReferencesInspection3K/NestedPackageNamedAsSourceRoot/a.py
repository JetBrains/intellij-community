from lib1 import m1
from lib1.m1 import x
from lib1.m1 import <error descr="Unresolved reference 'something'">something</error>


print(m1, x, something)
