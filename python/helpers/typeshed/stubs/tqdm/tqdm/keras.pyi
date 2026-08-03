from _typeshed import Incomplete

from tensorflow import keras

__all__ = ["TqdmCallback"]

class TqdmCallback(keras.callbacks.Callback):
    @staticmethod
    def bar2callback(bar, pop=None, delta=...): ...
    tqdm_class: Incomplete
    epoch_bar: Incomplete
    on_epoch_end: Incomplete
    batches: Incomplete
    verbose: Incomplete
    batch_bar: Incomplete
    on_batch_end: Incomplete
    def __init__(self, epochs=None, data_size=None, batch_size=None, verbose: int = 1, tqdm_class=..., **tqdm_kwargs) -> None: ...
    def on_train_begin(self, *_, **__) -> None: ...
    def on_epoch_begin(self, epoch, *_, **__) -> None: ...
    def on_train_end(self, *_, **__) -> None: ...
    def display(self) -> None: ...
