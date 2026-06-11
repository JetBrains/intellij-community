from m1 import g, Gen

g(<warning descr="Expected type 'dict[Any, Any]', got 'int' instead">Gen(10).get(10, 10)</warning>)
g(Gen(10).get<warning descr="No overload of 'get' matches the arguments. Argument types: (int, str). Expected one of: (x: int, y: int), (x: str, y: int)">(10, 'foo')</warning>)
g(Gen('foo').get<warning descr="No overload of 'get' matches the arguments. Argument types: (int, int). Expected one of: (x: int, y: str), (x: str, y: str)">(10, 10)</warning>)
g(<warning descr="Expected type 'dict[Any, Any]', got 'str' instead">Gen('foo').get(10, 'foo')</warning>)
