import io
from _typeshed import SupportsRead
from collections.abc import Collection
from pathlib import Path
from typing import Any, Protocol, TypeAlias, TypeVar, type_check_only
from typing_extensions import CapsuleType

import numpy as np

_T = TypeVar("_T")
_G = TypeVar("_G", bound=np.generic)
_G_co = TypeVar("_G_co", bound=np.generic, covariant=True)

@type_check_only
class SupportsArrowCStream(Protocol):
    def __arrow_c_stream__(self, requested_schema: object | None = None) -> CapsuleType: ...

@type_check_only
class SupportsArray(Protocol[_G_co]):
    def __array__(self) -> np.ndarray[Any, np.dtype[_G_co]]: ...

Array1D: TypeAlias = np.ndarray[tuple[int], np.dtype[_G]]
Array2D: TypeAlias = np.ndarray[tuple[int, int], np.dtype[_G]]
ReadPathOrBuffer: TypeAlias = str | Path | bytes | SupportsRead[bytes]
WritePathOrBuffer: TypeAlias = str | Path | io.BytesIO

DualArrayLike: TypeAlias = SupportsArray[_G] | Collection[_T] | _T
ArrayLikeInt: TypeAlias = DualArrayLike[np.bool | np.integer, int]
