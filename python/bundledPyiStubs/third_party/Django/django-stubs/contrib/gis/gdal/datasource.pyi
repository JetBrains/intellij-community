from typing import Any

from django.contrib.gis.gdal.base import GDALBase
from django.contrib.gis.gdal.driver import Driver
from django.contrib.gis.gdal.layer import Layer

class DataSource(GDALBase):
    destructor: Any
    encoding: str
    ptr: Any
    driver: Driver
    def __init__(self, ds_input: Any, ds_driver: bool = ..., write: bool = ..., encoding: str = ...) -> None: ...
    def __getitem__(self, index: str | int) -> Layer: ...
    def __len__(self) -> int: ...
    @property
    def layer_count(self) -> int: ...
    @property
    def name(self) -> str: ...
