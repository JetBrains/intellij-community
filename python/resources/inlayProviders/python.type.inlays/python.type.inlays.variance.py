from typing import TypeVar, Generic


class A[/*<# in #>*/T]:
    def f(self, t: T):
        pass


S = TypeVar("S", infer_variance=True)


class B(Generic[/*<# out #>*/S]):
    def f(self) -> S:
        pass
