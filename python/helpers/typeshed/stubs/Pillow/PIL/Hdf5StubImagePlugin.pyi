from .ImageFile import StubImageFile

def register_handler(handler) -> None: ...

class HDF5StubImageFile(StubImageFile):
    format: str
    format_description: str
