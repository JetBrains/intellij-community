from _typeshed import Self
from os import stat_result

class SFTPAttributes:
    FLAG_SIZE: int
    FLAG_UIDGID: int
    FLAG_PERMISSIONS: int
    FLAG_AMTIME: int
    FLAG_EXTENDED: int
    st_size: int | None
    st_uid: int | None
    st_gid: int | None
    st_mode: int | None
    st_atime: int | None
    st_mtime: int | None
    filename: str  # only when from_stat() is used
    longname: str  # only when from_stat() is used
    attr: dict[str, str]
    def __init__(self) -> None: ...
    @classmethod
    def from_stat(cls: type[Self], obj: stat_result, filename: str | None = ...) -> Self: ...
    def asbytes(self) -> bytes: ...
