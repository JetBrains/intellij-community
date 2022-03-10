from collections.abc import Mapping
from typing import Any, overload
from typing_extensions import Literal

from ..future.engine import Engine as FutureEngine
from .base import Engine
from .mock import MockConnection
from .url import URL

# Further kwargs are forwarded to the engine, dialect, or pool.
@overload
def create_engine(url: URL | str, *, strategy: Literal["mock"], **kwargs) -> MockConnection: ...  # type: ignore[misc]
@overload
def create_engine(
    url: URL | str, *, module: Any | None = ..., enable_from_linting: bool = ..., future: Literal[True], **kwargs
) -> FutureEngine: ...
@overload
def create_engine(
    url: URL | str, *, module: Any | None = ..., enable_from_linting: bool = ..., future: Literal[False] = ..., **kwargs
) -> Engine: ...
def engine_from_config(configuration: Mapping[str, Any], prefix: str = ..., **kwargs) -> Engine: ...
