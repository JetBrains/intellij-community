from typing import TypeAlias
from typing_extensions import deprecated

from rasterio._path import _ParsedPath, _UnparsedPath
from rasterio.errors import RasterioDeprecationWarning as RasterioDeprecationWarning

ParsedPath: TypeAlias = _ParsedPath
UnparsedPath: TypeAlias = _UnparsedPath

@deprecated("rasterio.path.parse_path is deprecated; use rasterio._path._parse_path or pass paths directly to rasterio.open.")
def parse_path(path: str) -> _ParsedPath | _UnparsedPath: ...
@deprecated("rasterio.path.vsi_path is deprecated; use rasterio._path._vsi_path directly.")
def vsi_path(path: _ParsedPath | _UnparsedPath) -> str: ...
