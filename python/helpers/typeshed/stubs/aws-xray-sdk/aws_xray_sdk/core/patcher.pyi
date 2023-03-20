from collections.abc import Iterable
from logging import Logger
from typing import Any

from aws_xray_sdk import global_sdk_config as global_sdk_config

from .utils.compat import PY2 as PY2, is_classmethod as is_classmethod, is_instance_method as is_instance_method

log: Logger
SUPPORTED_MODULES: Any
NO_DOUBLE_PATCH: Any

def patch_all(double_patch: bool = ...) -> None: ...
def patch(modules_to_patch: Iterable[str], raise_errors: bool = ..., ignore_module_patterns: str | None = ...) -> None: ...
