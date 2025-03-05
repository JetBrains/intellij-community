from typing import Callable, Any

class MyCallable[**P, R]:
    def __call__(self, *args: P.args, **kwargs: P.kwargs):
        ...
        
def g[**P](fn: Callable[P, Any]) -> MyCallable[P, Any]:
    ...

def f[*Ts](x: int, /, y: list[str], *args: *Ts, z: str, **kwargs: str) -> int:
    ...

ex<the_ref>pr = g(f)
