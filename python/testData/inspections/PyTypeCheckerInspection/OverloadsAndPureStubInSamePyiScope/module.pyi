import sys
from typing import overload

if sys.version_info >= (3, ):
    def foo(p: str) -> str: pass
else:
    @overload
    def foo(p: int) -> int: pass
    @overload
    def foo(p: str, i: int) -> str: pass

@overload
def bar(p: str) -> str: pass

@overload
def bar(p: int) -> int: pass
