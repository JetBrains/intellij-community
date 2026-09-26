from collections import UserDict
from typing import Any, ClassVar, Final

# A `Profile` is a dict of GDAL driver-specific dataset-creation options
# (e.g. `count`, `dtype`, `compress`); value types depend on the option.
class Profile(UserDict[str, Any]):
    defaults: ClassVar[dict[str, Any]]
    def __init__(self, data: dict[str, Any] = ..., **kwds: Any) -> None: ...
    def __getitem__(self, key: str) -> Any: ...
    def __setitem__(self, key: str, val: Any) -> None: ...

class DefaultGTiffProfile(Profile):
    defaults: ClassVar[dict[str, Any]]

default_gtiff_profile: Final[DefaultGTiffProfile]
