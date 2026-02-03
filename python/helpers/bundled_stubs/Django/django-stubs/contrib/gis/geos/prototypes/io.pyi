import threading
from ctypes import Structure
from typing import Any

from django.contrib.gis.geos.base import GEOSBase
from django.contrib.gis.geos.libgeos import GEOSFuncFactory

class WKTReader_st(Structure): ...
class WKTWriter_st(Structure): ...
class WKBReader_st(Structure): ...
class WKBWriter_st(Structure): ...

WKT_READ_PTR: Any
WKT_WRITE_PTR: Any
WKB_READ_PTR: Any
WKB_WRITE_PTR: Any
wkt_reader_create: Any
wkt_reader_destroy: Any
wkt_reader_read: Any
wkt_writer_create: Any
wkt_writer_destroy: Any
wkt_writer_write: Any
wkt_writer_get_outdim: Any
wkt_writer_set_outdim: Any
wkt_writer_set_trim: Any
wkt_writer_set_precision: Any
wkb_reader_create: Any
wkb_reader_destroy: Any

class WKBReadFunc(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

wkb_reader_read: Any
wkb_reader_read_hex: Any
wkb_writer_create: Any
wkb_writer_destroy: Any

class WKBWriteFunc(GEOSFuncFactory):
    argtypes: Any
    restype: Any
    errcheck: Any

wkb_writer_write: Any
wkb_writer_write_hex: Any

class WKBWriterGet(GEOSFuncFactory):
    argtypes: Any
    restype: Any

class WKBWriterSet(GEOSFuncFactory):
    argtypes: Any

wkb_writer_get_byteorder: Any
wkb_writer_set_byteorder: Any
wkb_writer_get_outdim: Any
wkb_writer_set_outdim: Any
wkb_writer_get_include_srid: Any
wkb_writer_set_include_srid: Any

class IOBase(GEOSBase):
    ptr: Any
    def __init__(self) -> None: ...

class _WKTReader(IOBase):
    ptr_type: Any
    destructor: Any
    def read(self, wkt: Any) -> Any: ...

class _WKBReader(IOBase):
    ptr_type: Any
    destructor: Any
    def read(self, wkb: Any) -> Any: ...

def default_trim_value() -> bool: ...

class WKTWriter(IOBase):
    ptr_type: Any
    destructor: Any
    def __init__(self, dim: int = ..., trim: bool = ..., precision: Any | None = ...) -> None: ...
    def write(self, geom: Any) -> Any: ...
    @property
    def outdim(self) -> Any: ...
    @outdim.setter
    def outdim(self, new_dim: Any) -> None: ...
    @property
    def trim(self) -> Any: ...
    @trim.setter
    def trim(self, flag: Any) -> None: ...
    @property
    def precision(self) -> Any: ...
    @precision.setter
    def precision(self, precision: Any) -> None: ...

class WKBWriter(IOBase):
    ptr_type: Any
    destructor: Any
    geos_version: Any
    def __init__(self, dim: int = ...) -> None: ...
    def write(self, geom: Any) -> Any: ...
    def write_hex(self, geom: Any) -> Any: ...
    byteorder: Any
    @property
    def outdim(self) -> Any: ...
    @outdim.setter
    def outdim(self, new_dim: Any) -> None: ...
    @property
    def srid(self) -> Any: ...
    @srid.setter
    def srid(self, include: Any) -> None: ...

class ThreadLocalIO(threading.local):
    wkt_r: Any
    wkt_w: Any
    wkb_r: Any
    wkb_w: Any
    ewkb_w: Any

thread_context: Any

def wkt_r() -> Any: ...
def wkt_w(dim: int = ..., trim: bool = ..., precision: Any | None = ...) -> Any: ...
def wkb_r() -> Any: ...
def wkb_w(dim: int = ...) -> Any: ...
def ewkb_w(dim: int = ...) -> Any: ...
