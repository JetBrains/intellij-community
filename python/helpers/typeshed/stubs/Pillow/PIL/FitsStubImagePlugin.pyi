from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class FITSStubImageFile(StubImageFile):
    format: str
    format_description: str
