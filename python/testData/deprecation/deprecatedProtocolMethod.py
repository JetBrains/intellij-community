from typing import Protocol
from typing_extensions import deprecated


class SupportsFoo(Protocol):
    @deprecated("foo is deprecated")
    def foo(self) -> None:
        ...

    def bar(self) -> None:
        ...


def foo_it(f: SupportsFoo) -> None:
    f.<warning descr="foo is deprecated">foo</warning>()
    f.bar()
