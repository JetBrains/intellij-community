from typing import Any, Optional

class DeleteMarker:
    bucket = ...  # type: Any
    name = ...  # type: Any
    version_id = ...  # type: Any
    is_latest = ...  # type: bool
    last_modified = ...  # type: Any
    owner = ...  # type: Any
    def __init__(self, bucket: Optional[Any] = ..., name: Optional[Any] = ...) -> None: ...
    def startElement(self, name, attrs, connection): ...
    def endElement(self, name, value, connection): ...
