from pathlib import Path

from ._typing import ReadPathOrBuffer

def get_vsi_path_or_buffer(path_or_buffer: ReadPathOrBuffer) -> str | bytes: ...
def vsi_path(path: str | Path) -> str: ...

SCHEMES: dict[str, str]
CURLSCHEMES: set[str]

def vsimem_rmtree_toplevel(path: str | Path) -> None: ...
