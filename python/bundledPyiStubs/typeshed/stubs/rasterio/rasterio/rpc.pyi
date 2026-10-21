from collections.abc import Sequence
from typing import Any
from typing_extensions import Self

class RPC:
    height_off: float
    height_scale: float
    lat_off: float
    lat_scale: float
    line_den_coeff: Sequence[float]
    line_num_coeff: Sequence[float]
    line_off: float
    line_scale: float
    long_off: float
    long_scale: float
    samp_den_coeff: Sequence[float]
    samp_num_coeff: Sequence[float]
    samp_off: float
    samp_scale: float
    err_bias: float | None
    err_rand: float | None
    def __init__(
        self,
        height_off: float,
        height_scale: float,
        lat_off: float,
        lat_scale: float,
        line_den_coeff: Sequence[float],
        line_num_coeff: Sequence[float],
        line_off: float,
        line_scale: float,
        long_off: float,
        long_scale: float,
        samp_den_coeff: Sequence[float],
        samp_num_coeff: Sequence[float],
        samp_off: float,
        samp_scale: float,
        err_bias: float | None = None,
        err_rand: float | None = None,
    ) -> None: ...
    def to_dict(self) -> dict[str, Any]: ...
    def to_gdal(self) -> dict[str, str]: ...
    @classmethod
    def from_gdal(cls, rpcs: dict[str, str]) -> Self: ...
