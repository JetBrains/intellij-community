from collections.abc import MutableMapping
from typing import Any

_Attrs = MutableMapping[Any, str]

def nofollow(attrs: _Attrs, new: bool = ...) -> _Attrs: ...
def target_blank(attrs: _Attrs, new: bool = ...) -> _Attrs: ...
