from _typeshed import Incomplete
from abc import ABC, abstractmethod
from collections.abc import Callable, Iterator as _Iterator, Sequence
from typing import Any, Generic, TypeVar, overload
from typing_extensions import Self

import numpy as np
import tensorflow as tf
from tensorflow import TypeSpec, _ScalarTensorCompatible, _TensorCompatible
from tensorflow._aliases import ContainerGeneric
from tensorflow.data import experimental as experimental
from tensorflow.data.experimental import AUTOTUNE as AUTOTUNE
from tensorflow.dtypes import DType
from tensorflow.io import _CompressionTypes
from tensorflow.python.trackable.base import Trackable

_T1 = TypeVar("_T1", covariant=True)
_T2 = TypeVar("_T2")
_T3 = TypeVar("_T3")

class Iterator(_Iterator[_T1], Trackable, ABC):
    @property
    @abstractmethod
    def element_spec(self) -> ContainerGeneric[TypeSpec[Any]]: ...
    @abstractmethod
    def get_next(self) -> _T1: ...
    @abstractmethod
    def get_next_as_optional(self) -> tf.experimental.Optional[_T1]: ...

class Dataset(ABC, Generic[_T1]):
    def apply(self, transformation_func: Callable[[Dataset[_T1]], Dataset[_T2]]) -> Dataset[_T2]: ...
    def as_numpy_iterator(self) -> Iterator[np.ndarray[Any, Any]]: ...
    def batch(
        self,
        batch_size: _ScalarTensorCompatible,
        drop_remainder: bool = False,
        num_parallel_calls: int | None = None,
        deterministic: bool | None = None,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def bucket_by_sequence_length(
        self,
        element_length_func: Callable[[_T1], _ScalarTensorCompatible],
        bucket_boundaries: Sequence[int],
        bucket_batch_sizes: Sequence[int],
        padded_shapes: ContainerGeneric[tf.TensorShape | _TensorCompatible] | None = None,
        padding_values: ContainerGeneric[_ScalarTensorCompatible] | None = None,
        pad_to_bucket_boundary: bool = False,
        no_padding: bool = False,
        drop_remainder: bool = False,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def cache(self, filename: str = "", name: str | None = None) -> Dataset[_T1]: ...
    def cardinality(self) -> int: ...
    @staticmethod
    def choose_from_datasets(
        datasets: Sequence[Dataset[_T2]], choice_dataset: Dataset[tf.Tensor], stop_on_empty_dataset: bool = True
    ) -> Dataset[_T2]: ...
    def concatenate(self, dataset: Dataset[_T1], name: str | None = None) -> Dataset[_T1]: ...
    @staticmethod
    def counter(
        start: _ScalarTensorCompatible = 0, step: _ScalarTensorCompatible = 1, dtype: DType = ..., name: str | None = None
    ) -> Dataset[tf.Tensor]: ...
    @property
    @abstractmethod
    def element_spec(self) -> ContainerGeneric[TypeSpec[Any]]: ...
    def enumerate(self, start: _ScalarTensorCompatible = 0, name: str | None = None) -> Dataset[tuple[int, _T1]]: ...
    def filter(self, predicate: Callable[[_T1], bool | tf.Tensor], name: str | None = None) -> Dataset[_T1]: ...
    def flat_map(self, map_func: Callable[[_T1], Dataset[_T2]], name: str | None = None) -> Dataset[_T2]: ...
    # PEP 646 can be used here for a more precise type when better supported.
    @staticmethod
    def from_generator(
        generator: Callable[..., _T2],
        output_types: ContainerGeneric[DType] | None = None,
        output_shapes: ContainerGeneric[tf.TensorShape | Sequence[int | None]] | None = None,
        args: tuple[object, ...] | None = None,
        output_signature: ContainerGeneric[TypeSpec[Any]] | None = None,
        name: str | None = None,
    ) -> Dataset[_T2]: ...
    @staticmethod
    def from_tensors(tensors: Any, name: str | None = None) -> Dataset[Any]: ...
    @staticmethod
    def from_tensor_slices(tensors: _TensorCompatible, name: str | None = None) -> Dataset[Any]: ...
    def get_single_element(self, name: str | None = None) -> _T1: ...
    def group_by_window(
        self,
        key_func: Callable[[_T1], tf.Tensor],
        reduce_func: Callable[[tf.Tensor, Dataset[_T1]], Dataset[_T2]],
        window_size: _ScalarTensorCompatible | None = None,
        window_size_func: Callable[[tf.Tensor], tf.Tensor] | None = None,
        name: str | None = None,
    ) -> Dataset[_T2]: ...
    def ignore_errors(self, log_warning: bool = False, name: str | None = None) -> Dataset[_T1]: ...
    def interleave(
        self,
        map_func: Callable[[_T1], Dataset[_T2]],
        cycle_length: int | None = None,
        block_length: int | None = None,
        num_parallel_calls: int | None = None,
        deterministic: bool | None = None,
        name: str | None = None,
    ) -> Dataset[_T2]: ...
    def __iter__(self) -> Iterator[_T1]: ...
    @staticmethod
    def list_files(
        file_pattern: str | Sequence[str] | _TensorCompatible,
        shuffle: bool | None = None,
        seed: int | None = None,
        name: str | None = None,
    ) -> Dataset[str]: ...
    @staticmethod
    def load(
        path: str,
        element_spec: ContainerGeneric[tf.TypeSpec[Any]] | None = None,
        compression: _CompressionTypes = None,
        reader_func: Callable[[Dataset[Dataset[Any]]], Dataset[Any]] | None = None,
    ) -> Dataset[Any]: ...
    # PEP 646 could be used here for a more precise type when better supported.
    def map(
        self,
        map_func: Callable[..., _T2],
        num_parallel_calls: int | None = None,
        deterministic: None | bool = None,
        name: str | None = None,
    ) -> Dataset[_T2]: ...
    def options(self) -> Options: ...
    def padded_batch(
        self,
        batch_size: _ScalarTensorCompatible,
        padded_shapes: ContainerGeneric[tf.TensorShape | _TensorCompatible] | None = None,
        padding_values: ContainerGeneric[_ScalarTensorCompatible] | None = None,
        drop_remainder: bool = False,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def prefetch(self, buffer_size: _ScalarTensorCompatible, name: str | None = None) -> Dataset[_T1]: ...
    def ragged_batch(
        self,
        batch_size: _ScalarTensorCompatible,
        drop_remainder: bool = False,
        row_splits_dtype: DType = ...,
        name: str | None = None,
    ) -> Dataset[tf.RaggedTensor]: ...
    @staticmethod
    def random(
        seed: int | None = None, rerandomize_each_iteration: bool | None = None, name: str | None = None
    ) -> Dataset[tf.Tensor]: ...
    @staticmethod
    @overload
    def range(__stop: _ScalarTensorCompatible, output_type: DType = ..., name: str | None = None) -> Dataset[tf.Tensor]: ...
    @staticmethod
    @overload
    def range(
        __start: _ScalarTensorCompatible,
        __stop: _ScalarTensorCompatible,
        __step: _ScalarTensorCompatible = 1,
        output_type: DType = ...,
        name: str | None = None,
    ) -> Dataset[tf.Tensor]: ...
    def rebatch(
        self, batch_size: _ScalarTensorCompatible, drop_remainder: bool = False, name: str | None = None
    ) -> Dataset[_T1]: ...
    def reduce(self, initial_state: _T2, reduce_func: Callable[[_T2, _T1], _T2], name: str | None = None) -> _T2: ...
    def rejection_resample(
        self,
        class_func: Callable[[_T1], _ScalarTensorCompatible],
        target_dist: _TensorCompatible,
        initial_dist: _TensorCompatible | None = None,
        seed: int | None = None,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def repeat(self, count: _ScalarTensorCompatible | None = None, name: str | None = None) -> Dataset[_T1]: ...
    @staticmethod
    def sample_from_datasets(
        datasets: Sequence[Dataset[_T1]],
        weights: _TensorCompatible | None = None,
        seed: int | None = None,
        stop_on_empty_dataset: bool = False,
        rerandomize_each_iteration: bool | None = None,
    ) -> Dataset[_T1]: ...
    # Incomplete as tf.train.CheckpointOptions not yet covered.
    def save(
        self,
        path: str,
        compression: _CompressionTypes = None,
        shard_func: Callable[[_T1], int] | None = None,
        checkpoint_args: Incomplete | None = None,
    ) -> None: ...
    def scan(
        self, initial_state: _T2, scan_func: Callable[[_T2, _T1], tuple[_T2, _T3]], name: str | None = None
    ) -> Dataset[_T3]: ...
    def shard(
        self, num_shards: _ScalarTensorCompatible, index: _ScalarTensorCompatible, name: str | None = None
    ) -> Dataset[_T1]: ...
    def shuffle(
        self,
        buffer_size: _ScalarTensorCompatible,
        seed: int | None = None,
        reshuffle_each_iteration: bool | None = None,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def skip(self, count: _ScalarTensorCompatible, name: str | None = None) -> Dataset[_T1]: ...
    def snapshot(
        self,
        path: str,
        compression: _CompressionTypes = "AUTO",
        reader_func: Callable[[Dataset[Dataset[_T1]]], Dataset[_T1]] | None = None,
        shard_func: Callable[[_T1], _ScalarTensorCompatible] | None = None,
        name: str | None = None,
    ) -> Dataset[_T1]: ...
    def sparse_batch(
        self, batch_size: _ScalarTensorCompatible, row_shape: tf.TensorShape | _TensorCompatible, name: str | None = None
    ) -> Dataset[tf.SparseTensor]: ...
    def take(self, count: _ScalarTensorCompatible, name: str | None = None) -> Dataset[_T1]: ...
    def take_while(self, predicate: Callable[[_T1], _ScalarTensorCompatible], name: str | None = None) -> Dataset[_T1]: ...
    def unbatch(self, name: str | None = None) -> Dataset[_T1]: ...
    def unique(self, name: str | None = None) -> Dataset[_T1]: ...
    def window(
        self,
        size: _ScalarTensorCompatible,
        shift: _ScalarTensorCompatible | None = None,
        stride: _ScalarTensorCompatible = 1,
        drop_remainder: bool = False,
        name: str | None = None,
    ) -> Dataset[Dataset[_T1]]: ...
    def with_options(self, options: Options, name: str | None = None) -> Dataset[_T1]: ...
    @staticmethod
    def zip(datasets: tuple[Dataset[_T2], Dataset[_T3]], name: str | None = None) -> Dataset[tuple[_T2, _T3]]: ...
    def __len__(self) -> int: ...
    def __nonzero__(self) -> bool: ...
    def __getattr__(self, name: str) -> Incomplete: ...

class Options:
    autotune: Incomplete
    deterministic: bool
    experimental_deterministic: bool
    experimental_distribute: Incomplete
    experimental_external_state_policy: Incomplete
    experimental_optimization: Incomplete
    experimental_slack: bool
    experimental_symbolic_checkpoint: bool
    experimental_threading: Incomplete
    threading: Incomplete
    def merge(self, options: Options) -> Self: ...

class TFRecordDataset(Dataset[tf.Tensor]):
    def __init__(
        self,
        filenames: _TensorCompatible | Dataset[str],
        compression_type: _CompressionTypes = None,
        buffer_size: int | None = None,
        num_parallel_reads: int | None = None,
        name: str | None = None,
    ) -> None: ...
    @property
    def element_spec(self) -> tf.TensorSpec: ...

def __getattr__(name: str) -> Incomplete: ...
