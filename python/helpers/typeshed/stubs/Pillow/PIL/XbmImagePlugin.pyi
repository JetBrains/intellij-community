from typing import Any

from .ImageFile import ImageFile

xbm_head: Any

class XbmImageFile(ImageFile):
    format: str
    format_description: str
