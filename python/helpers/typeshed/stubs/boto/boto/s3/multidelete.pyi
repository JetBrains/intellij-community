from typing import Any

class Deleted:
    key: Any
    version_id: Any
    delete_marker: Any
    delete_marker_version_id: Any
    def __init__(
        self,
        key: Any | None = ...,
        version_id: Any | None = ...,
        delete_marker: bool = ...,
        delete_marker_version_id: Any | None = ...,
    ) -> None: ...
    def startElement(self, name, attrs, connection): ...
    def endElement(self, name, value, connection): ...

class Error:
    key: Any
    version_id: Any
    code: Any
    message: Any
    def __init__(
        self, key: Any | None = ..., version_id: Any | None = ..., code: Any | None = ..., message: Any | None = ...
    ) -> None: ...
    def startElement(self, name, attrs, connection): ...
    def endElement(self, name, value, connection): ...

class MultiDeleteResult:
    bucket: Any
    deleted: Any
    errors: Any
    def __init__(self, bucket: Any | None = ...) -> None: ...
    def startElement(self, name, attrs, connection): ...
    def endElement(self, name, value, connection): ...
