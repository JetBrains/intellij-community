from _typeshed import StrOrBytesPath
from typing import Any

_OpenFile = StrOrBytesPath | int

def main(template: _OpenFile, data: _OpenFile | None = ..., **kwargs: Any) -> str: ...
def cli_main() -> None: ...
