from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class BufrStubImageFile(StubImageFile):
    format: str
    format_description: str
