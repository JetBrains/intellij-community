from collections.abc import Sequence
from ipaddress import IPv4Address, IPv6Address
from typing import Final, TypeAlias, TypedDict

from _typeshed import StrPath
from django.contrib.gis.geos import Point
from django.utils.functional import cached_property
from typing_extensions import Self, deprecated

HAS_GEOIP2: bool
SUPPORTED_DATABASE_TYPES: set[str]

_CoordType: TypeAlias = tuple[float, float] | tuple[None, None]
_QueryType: TypeAlias = str | IPv4Address | IPv6Address

class _CityResponse(TypedDict):
    accuracy_radius: int | None
    city: str
    continent_code: str
    continent_name: str
    country_code: str
    country_name: str
    is_in_european_union: bool
    latitude: float
    longitude: float
    metro_code: int | None
    postal_code: str | None
    region_code: str | None
    region_name: str | None
    time_zone: str | None
    # Kept for backward compatibility.
    dma_code: int | None
    region: str | None

class _CountryResponse(TypedDict):
    continent_code: str
    continent_name: str
    country_code: str
    country_name: str
    is_in_european_union: bool

class GeoIP2Exception(Exception): ...

class GeoIP2:
    MODE_AUTO: Final = 0
    MODE_MMAP_EXT: Final = 1
    MODE_MMAP: Final = 2
    MODE_FILE: Final = 4
    MODE_MEMORY: Final = 8
    cache_options: Final[frozenset[int]]

    def __init__(
        self,
        path: StrPath | None = None,
        cache: int = 0,
        country: str | None = None,
        city: str | None = None,
    ) -> None: ...
    def __del__(self) -> None: ...
    @cached_property
    def is_city(self) -> bool: ...
    @cached_property
    def is_country(self) -> bool: ...
    def city(self, query: _QueryType) -> _CityResponse: ...
    def country_code(self, query: _QueryType) -> str: ...
    def country_name(self, query: _QueryType) -> str: ...
    def country(self, query: _QueryType) -> _CountryResponse: ...
    @deprecated("coords() is deprecated and will be removed in Django 6.0. Use lon_lat() instead.")
    def coords(self, query: _QueryType, ordering: Sequence[str] = ...) -> _CoordType: ...
    def lon_lat(self, query: _QueryType) -> _CoordType: ...
    def lat_lon(self, query: _QueryType) -> _CoordType: ...
    def geos(self, query: _QueryType) -> Point: ...
    @classmethod
    @deprecated("open() is deprecated and will be removed in Django 6.0. Use GeoIP2() instead.")
    def open(cls, full_path: StrPath | None, cache: int) -> Self: ...

__all__ = ["HAS_GEOIP2"]
