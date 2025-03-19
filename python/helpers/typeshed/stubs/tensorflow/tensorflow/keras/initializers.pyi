from _typeshed import Incomplete
from collections.abc import Callable
from typing import Any, overload
from typing_extensions import Self, TypeAlias

from tensorflow import Tensor, _DTypeLike, _ShapeLike, _TensorCompatible

class Initializer:
    def __call__(self, shape: _ShapeLike, dtype: _DTypeLike | None = None) -> Tensor: ...
    def get_config(self) -> dict[str, Any]: ...
    @classmethod
    def from_config(cls, config: dict[str, Any]) -> Self: ...

class Constant(Initializer):
    def __init__(self, value: _TensorCompatible = 0) -> None: ...

class GlorotNormal(Initializer):
    def __init__(self, seed: int | None = None) -> None: ...

class GlorotUniform(Initializer):
    def __init__(self, seed: int | None = None) -> None: ...

class TruncatedNormal(Initializer):
    def __init__(self, mean: _TensorCompatible = 0.0, stddev: _TensorCompatible = 0.05, seed: int | None = None) -> None: ...

class RandomNormal(Initializer):
    def __init__(self, mean: _TensorCompatible = 0.0, stddev: _TensorCompatible = 0.05, seed: int | None = None) -> None: ...

class RandomUniform(Initializer):
    def __init__(self, minval: _TensorCompatible = -0.05, maxval: _TensorCompatible = 0.05, seed: int | None = None) -> None: ...

class Zeros(Initializer): ...

constant = Constant
glorot_normal = GlorotNormal
glorot_uniform = GlorotUniform
truncated_normal = TruncatedNormal
zeros = Zeros

_Initializer: TypeAlias = (  # noqa: Y047
    str | Initializer | type[Initializer] | Callable[[_ShapeLike], Tensor] | dict[str, Any] | None
)

@overload
def get(identifier: None) -> None: ...
@overload
def get(identifier: str | Initializer | dict[str, Any] | type[Initializer]) -> Initializer: ...
@overload
def get(identifier: Callable[[_ShapeLike], Tensor]) -> Callable[[_ShapeLike], Tensor]: ...
def __getattr__(name: str) -> Incomplete: ...
