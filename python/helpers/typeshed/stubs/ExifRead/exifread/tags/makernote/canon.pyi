from collections.abc import Callable
from typing import Any
from typing_extensions import TypeAlias

from exifread._types import TagDict

TAGS: TagDict

CAMERA_SETTINGS: TagDict
FOCAL_LENGTH: TagDict
SHOT_INFO: TagDict
AF_INFO_2: TagDict
FILE_INFO: TagDict

def add_one(value: int) -> int: ...
def subtract_one(value: int) -> int: ...
def convert_temp(value: int) -> str: ...

_CameraInfo: TypeAlias = dict[int, tuple[str, str, Callable[[int], Any]]]

CAMERA_INFO_TAG_NAME: str
CAMERA_INFO_5D: _CameraInfo
CAMERA_INFO_5DMKII: _CameraInfo
CAMERA_INFO_5DMKIII: _CameraInfo
CAMERA_INFO_600D: _CameraInfo
CAMERA_INFO_MODEL_MAP: dict[str, _CameraInfo]
