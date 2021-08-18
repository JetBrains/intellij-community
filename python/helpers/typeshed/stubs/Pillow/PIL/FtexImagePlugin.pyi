from .ImageFile import ImageFile

MAGIC: bytes
FORMAT_DXT1: int
FORMAT_UNCOMPRESSED: int

class FtexImageFile(ImageFile):
    format: str
    format_description: str
    def load_seek(self, pos) -> None: ...
