from _typeshed import Incomplete
from typing import Any
from typing_extensions import TypeAlias

__all__ = ["TqdmCallback"]

_Callback: TypeAlias = Any  # Actually tensorflow.keras.callbacks.Callback

class TqdmCallback(_Callback):
    @staticmethod
    def bar2callback(bar, pop: Incomplete | None = ..., delta=...): ...
    tqdm_class: Incomplete
    epoch_bar: Incomplete
    on_epoch_end: Incomplete
    batches: Incomplete
    verbose: Incomplete
    batch_bar: Incomplete
    on_batch_end: Incomplete
    def __init__(
        self,
        epochs: Incomplete | None = ...,
        data_size: Incomplete | None = ...,
        batch_size: Incomplete | None = ...,
        verbose: int = ...,
        tqdm_class=...,
        **tqdm_kwargs,
    ) -> None: ...
    def on_train_begin(self, *_, **__) -> None: ...
    def on_epoch_begin(self, epoch, *_, **__) -> None: ...
    def on_train_end(self, *_, **__) -> None: ...
    def display(self) -> None: ...
