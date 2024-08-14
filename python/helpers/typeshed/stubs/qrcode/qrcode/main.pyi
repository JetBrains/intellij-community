from _typeshed import ConvertibleToInt, Incomplete
from typing import Any, Generic, Literal, NamedTuple, TypeVar, overload
from typing_extensions import TypeAlias

from .image.base import BaseImage
from .util import QRData, _MaskPattern

ModulesType: TypeAlias = list[list[bool | None]]
precomputed_qr_blanks: dict[int, ModulesType]

_DefaultImage: TypeAlias = Any  # PilImage if Pillow is installed, PyPNGImage otherwise

@overload
def make(
    data: QRData | bytes | str,
    *,
    version: ConvertibleToInt | None = None,
    error_correction: Literal[0, 1, 2, 3] = 0,
    box_size: ConvertibleToInt = 10,
    border: ConvertibleToInt = 4,
    image_factory: None = None,
    mask_pattern: _MaskPattern | None = None,
) -> _DefaultImage: ...
@overload
def make(
    data: QRData | bytes | str,
    *,
    version: ConvertibleToInt | None = None,
    error_correction: Literal[0, 1, 2, 3] = 0,
    box_size: ConvertibleToInt = 10,
    border: ConvertibleToInt = 4,
    image_factory: type[GenericImage],
    mask_pattern: _MaskPattern | None = None,
) -> GenericImage: ...
def copy_2d_array(x): ...

class ActiveWithNeighbors(NamedTuple):
    NW: bool
    N: bool
    NE: bool
    W: bool
    me: bool
    E: bool
    SW: bool
    S: bool
    SE: bool
    def __bool__(self) -> bool: ...

GenericImage = TypeVar("GenericImage", bound=BaseImage)  # noqa: Y001
GenericImageLocal = TypeVar("GenericImageLocal", bound=BaseImage)  # noqa: Y001

class QRCode(Generic[GenericImage]):
    modules: ModulesType
    error_correction: Literal[0, 1, 2, 3]
    box_size: int
    border: int
    image_factory: type[GenericImage] | None
    @overload
    def __init__(
        self,
        version: ConvertibleToInt | None,
        error_correction: Literal[0, 1, 2, 3],
        box_size: ConvertibleToInt,
        border: ConvertibleToInt,
        image_factory: type[GenericImage],
        mask_pattern: _MaskPattern | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self,
        version: ConvertibleToInt | None = None,
        error_correction: Literal[0, 1, 2, 3] = 0,
        box_size: ConvertibleToInt = 10,
        border: ConvertibleToInt = 4,
        *,
        image_factory: type[GenericImage],
        mask_pattern: _MaskPattern | None = None,
    ) -> None: ...
    @overload
    def __init__(
        self: QRCode[_DefaultImage],
        version: ConvertibleToInt | None = None,
        error_correction: Literal[0, 1, 2, 3] = 0,
        box_size: ConvertibleToInt = 10,
        border: ConvertibleToInt = 4,
        image_factory: None = None,
        mask_pattern: _MaskPattern | None = None,
    ) -> None: ...
    @property
    def version(self) -> int: ...
    @version.setter
    def version(self, value: ConvertibleToInt | None) -> None: ...
    @property
    def mask_pattern(self) -> _MaskPattern | None: ...
    @mask_pattern.setter
    def mask_pattern(self, pattern: _MaskPattern | None) -> None: ...
    modules_count: int
    data_cache: Incomplete
    data_list: Incomplete
    def clear(self) -> None: ...
    def add_data(self, data: QRData | bytes | str, optimize: int = 20) -> None: ...
    def make(self, fit: bool = True) -> None: ...
    def makeImpl(self, test, mask_pattern) -> None: ...
    def setup_position_probe_pattern(self, row, col) -> None: ...
    def best_fit(self, start: Incomplete | None = None): ...
    def best_mask_pattern(self): ...
    def print_tty(self, out: Incomplete | None = None) -> None: ...
    def print_ascii(self, out: Incomplete | None = None, tty: bool = False, invert: bool = False): ...
    @overload
    def make_image(self, image_factory: None = None, **kwargs: Any) -> GenericImage: ...
    @overload
    def make_image(self, image_factory: type[GenericImageLocal], **kwargs: Any) -> GenericImageLocal: ...
    def is_constrained(self, row: int, col: int) -> bool: ...
    def setup_timing_pattern(self) -> None: ...
    def setup_position_adjust_pattern(self) -> None: ...
    def setup_type_number(self, test) -> None: ...
    def setup_type_info(self, test, mask_pattern) -> None: ...
    def map_data(self, data, mask_pattern) -> None: ...
    def get_matrix(self): ...
    def active_with_neighbors(self, row: int, col: int) -> ActiveWithNeighbors: ...
