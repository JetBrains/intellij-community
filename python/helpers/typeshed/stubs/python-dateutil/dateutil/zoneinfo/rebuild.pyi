from _typeshed import StrOrBytesPath
from collections.abc import Sequence
from tarfile import TarInfo

def rebuild(
    filename: StrOrBytesPath, tag=None, format: str = "gz", zonegroups: Sequence[str | TarInfo] = [], metadata=None
) -> None: ...
