from m1 import <error descr="Unresolved reference 'foo'">foo</error>
from m1 import <error descr="Unresolved reference 'bar'">bar</error>
from m1 import bar_imported
from m1 import <error descr="Unresolved reference 'm2'">m2</error>
from m1 import m2_imported

print(foo, bar, bar_imported, m2, m2_imported)
