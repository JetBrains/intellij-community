(x): int = 42
(((x))): float
x['foo']: str
f(42).attr: dict

<error descr="An illegal target for a variable annotation">2 ** 8</error>: int
<error descr="An illegal target for a variable annotation">f()</error>: bool
<error descr="A variable annotation cannot be combined with tuple unpacking">x, y, z</error>: Tuple[int, ...]
(<error descr="A variable annotation cannot be combined with tuple unpacking">x, y, z</error>): Tuple[int, int, int]
<error descr="A variable annotation cannot be combined with tuple unpacking">[x, y, z]</error>: Tuple[Any, Any, Any]
<error descr="A variable annotation cannot be combined with tuple unpacking">x, *xs</error>: tuple = range(10)
<error descr="A variable annotation cannot be used in assignment with multiple targets">x:int = y = 42</error>