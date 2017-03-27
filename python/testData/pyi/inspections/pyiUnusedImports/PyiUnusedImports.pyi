from m1 import foo
<warning descr="Unused import statement">from m1 import bar</warning>
from m1 import bar as baz
from m2 import *
from m3 import <warning descr="Unused import statement">spam</warning>, eggs

foo2 = foo
eggs2 = eggs
