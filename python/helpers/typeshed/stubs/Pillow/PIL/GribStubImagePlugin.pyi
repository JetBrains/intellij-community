from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class GribStubImageFile(StubImageFile):
    format: str
    format_description: str
