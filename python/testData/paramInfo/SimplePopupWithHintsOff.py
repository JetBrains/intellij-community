def foo(a: int, b: str, c: bool, d: list, e: set): ...
foo(<arg1>)

def foo1(a: int, b: str, c: bool, d: list, e: set): ...
foo1(1, <arg2>)

def foo2(a: int, b: str, c: bool, d: list, e: set): ...
foo2(1, "2", <arg3>)

def foo3(a: int, b: str, c: bool, d: list, e: set): ..
foo3(1, '2', TRUE, <arg4>)

def foo4(a: int, b: str, c: bool, d: list, e: set): ...
foo4(1, "2", TRUE, [1], <arg5>)