from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from mymodule import SomeClass

def func(x: "SomeClass") -> None:
    ...