from typing import overload

if undefined:
    def foo(p: str) -> str: pass
else:
    @overload
    def foo(p: int) -> int: pass
    @overload
    def foo(p: str, i: int) -> str: pass

def bar(p: str) -> str: pass

@overload
def bar(p: int) -> int: pass
