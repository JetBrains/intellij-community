from collections.abc import Iterable
from logging import Logger

log: Logger
SUPPORTED_MODULES: tuple[str, ...]
NO_DOUBLE_PATCH: tuple[str, ...]

def patch_all(double_patch: bool = False) -> None: ...
def patch(
    modules_to_patch: Iterable[str], raise_errors: bool = True, ignore_module_patterns: Iterable[str] | None = None
) -> None: ...
