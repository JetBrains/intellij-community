import os
from collections.abc import Callable, Iterable
from typing import Any, Final

class JSONSequenceTool:
    func: Callable[..., Iterable[Any]]
    def __init__(self, func: Callable[..., Iterable[Any]]) -> None: ...
    def __call__(
        self,
        src_path: str | os.PathLike[str],
        dst_path: str | os.PathLike[str],
        src_kwargs: dict[str, Any] | None = None,
        dst_kwargs: dict[str, Any] | None = None,
        func_args: Iterable[Any] | None = None,
        func_kwargs: dict[str, Any] | None = None,
        config: dict[str, Any] | None = None,
    ) -> None: ...

dataset_features_tool: Final[JSONSequenceTool]
