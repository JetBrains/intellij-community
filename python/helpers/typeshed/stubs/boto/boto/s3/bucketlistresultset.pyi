from collections.abc import Iterable, Iterator
from typing import Any

from .key import Key

def bucket_lister(
    bucket, prefix: str = ..., delimiter: str = ..., marker: str = ..., headers: Any | None = ..., encoding_type: Any | None = ...
): ...

class BucketListResultSet(Iterable[Key]):
    bucket: Any
    prefix: Any
    delimiter: Any
    marker: Any
    headers: Any
    encoding_type: Any
    def __init__(
        self,
        bucket: Any | None = ...,
        prefix: str = ...,
        delimiter: str = ...,
        marker: str = ...,
        headers: Any | None = ...,
        encoding_type: Any | None = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[Key]: ...

def versioned_bucket_lister(
    bucket,
    prefix: str = ...,
    delimiter: str = ...,
    key_marker: str = ...,
    version_id_marker: str = ...,
    headers: Any | None = ...,
    encoding_type: Any | None = ...,
): ...

class VersionedBucketListResultSet:
    bucket: Any
    prefix: Any
    delimiter: Any
    key_marker: Any
    version_id_marker: Any
    headers: Any
    encoding_type: Any
    def __init__(
        self,
        bucket: Any | None = ...,
        prefix: str = ...,
        delimiter: str = ...,
        key_marker: str = ...,
        version_id_marker: str = ...,
        headers: Any | None = ...,
        encoding_type: Any | None = ...,
    ) -> None: ...
    def __iter__(self) -> Iterator[Key]: ...

def multipart_upload_lister(
    bucket, key_marker: str = ..., upload_id_marker: str = ..., headers: Any | None = ..., encoding_type: Any | None = ...
): ...

class MultiPartUploadListResultSet:
    bucket: Any
    key_marker: Any
    upload_id_marker: Any
    headers: Any
    encoding_type: Any
    def __init__(
        self,
        bucket: Any | None = ...,
        key_marker: str = ...,
        upload_id_marker: str = ...,
        headers: Any | None = ...,
        encoding_type: Any | None = ...,
    ) -> None: ...
    def __iter__(self): ...
