from m1 import g, Gen

g(<warning descr="Expected type 'dict', got 'int' instead">Gen(10).get(10, 10)</warning>)
g(Gen(10).get(10, <weak_warning descr="Expected type 'int' (matched generic type 'TypeVar('T')'), got 'str' instead">'foo'</weak_warning>))
g(Gen('foo').get(10, <weak_warning descr="Expected type 'str' (matched generic type 'TypeVar('T')'), got 'int' instead">10</weak_warning>))
g(<warning descr="Expected type 'dict', got 'str' instead">Gen('foo').get(10, 'foo')</warning>)
