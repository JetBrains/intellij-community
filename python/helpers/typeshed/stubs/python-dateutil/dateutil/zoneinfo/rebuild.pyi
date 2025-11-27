from _typeshed import StrOrBytesPath
from collections.abc import Iterable

from ..zoneinfo import _MetadataType

def rebuild(
    filename: StrOrBytesPath, tag=None, format: str = "gz", zonegroups: Iterable[str] = [], metadata: _MetadataType | None = None
) -> None: ...
