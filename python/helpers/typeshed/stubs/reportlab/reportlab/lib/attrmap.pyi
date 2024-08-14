from _typeshed import Incomplete
from typing import Final

__version__: Final[str]

class CallableValue:
    func: Incomplete
    args: Incomplete
    kw: Incomplete
    def __init__(self, func, *args, **kw) -> None: ...
    def __call__(self): ...

class AttrMapValue:
    validate: Incomplete
    desc: Incomplete
    def __init__(
        self,
        validate: Incomplete | None = None,
        desc: Incomplete | None = None,
        initial: Incomplete | None = None,
        advancedUsage: int = 0,
        **kw,
    ) -> None: ...
    def __getattr__(self, name): ...

class AttrMap(dict[str, AttrMapValue]):
    def __init__(self, BASE: Incomplete | None = None, UNWANTED=[], **kw) -> None: ...
    def remove(self, unwanted) -> None: ...
    def clone(self, UNWANTED=[], **kw): ...

def validateSetattr(obj, name, value) -> None: ...
def hook__setattr__(obj): ...
def addProxyAttribute(
    src,
    name,
    validate: Incomplete | None = None,
    desc: Incomplete | None = None,
    initial: Incomplete | None = None,
    dst: Incomplete | None = None,
) -> None: ...
