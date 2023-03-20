from _typeshed import Incomplete
from typing import Any
from typing_extensions import TypeAlias

__all__ = ["TqdmCallback"]

_Callback: TypeAlias = Any  # Actually dask.callbacks.Callback

class TqdmCallback(_Callback):
    tqdm_class: type[Incomplete]
    def __init__(
        self, start: Incomplete | None = ..., pretask: Incomplete | None = ..., tqdm_class: type[Incomplete] = ..., **tqdm_kwargs
    ) -> None: ...
    def display(self) -> None: ...
