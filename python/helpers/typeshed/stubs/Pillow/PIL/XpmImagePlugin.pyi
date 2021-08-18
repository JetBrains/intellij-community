from typing import Any

from .ImageFile import ImageFile

xpm_head: Any

class XpmImageFile(ImageFile):
    format: str
    format_description: str
    def load_read(self, bytes): ...
