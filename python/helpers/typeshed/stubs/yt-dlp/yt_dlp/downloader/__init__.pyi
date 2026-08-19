from collections.abc import Mapping
from typing import Any

from ..extractor.common import _InfoDict
from ..utils._utils import NO_DEFAULT
from .common import FileDownloader

__all__ = ["FileDownloader", "get_suitable_downloader", "shorten_protocol_name"]

def get_suitable_downloader(
    info_dict: _InfoDict,
    params: Mapping[str, Any] = {},
    default: FileDownloader | type[NO_DEFAULT] = ...,
    protocol: str | None = None,
    to_stdout: bool = False,
) -> FileDownloader: ...
def shorten_protocol_name(proto: str, simplify: bool = False) -> str: ...
