from .ImageFile import ImageFile

class FliImageFile(ImageFile):
    format: str
    format_description: str
    def seek(self, frame) -> None: ...
    def tell(self): ...
