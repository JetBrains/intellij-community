from typing import IO

from _typeshed import StrPath
from django.core.files import File

class ImageFile(File[bytes]):
    @property
    def width(self) -> int: ...
    @property
    def height(self) -> int: ...

def get_image_dimensions(file_or_path: StrPath | IO[bytes], close: bool = False) -> tuple[int | None, int | None]: ...
