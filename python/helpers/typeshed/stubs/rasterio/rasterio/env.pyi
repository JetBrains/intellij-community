import logging
import threading
from collections.abc import Callable, Iterable
from types import TracebackType
from typing import Any, Final, TypeVar
from typing_extensions import Self, deprecated

from rasterio._env import (
    GDALDataFinder as GDALDataFinder,
    GDALEnv as GDALEnv,
    PROJDataFinder as PROJDataFinder,
    _GDALOption,
    get_gdal_config as get_gdal_config,
    set_gdal_config as set_gdal_config,
    set_proj_data_search_path as set_proj_data_search_path,
)
from rasterio.errors import (
    EnvError as EnvError,
    GDALVersionError as GDALVersionError,
    RasterioDeprecationWarning as RasterioDeprecationWarning,
)
from rasterio.session import DummySession as DummySession, Session as Session

_F = TypeVar("_F", bound=Callable[..., Any])

class ThreadEnv(threading.local):
    def __init__(self) -> None: ...

local: ThreadEnv
log: logging.Logger

class Env:
    session: Session
    options: dict[str, _GDALOption]
    context_options: dict[str, _GDALOption]
    def __init__(
        self,
        session: Session | None = None,
        aws_unsigned: bool = False,
        profile_name: str | None = None,
        session_class: Callable[..., Session] = ...,
        **options: _GDALOption,
    ) -> None: ...
    @classmethod
    def default_options(cls) -> dict[str, _GDALOption]: ...
    # Forwarded to `cls(...)` after merging in default_options(); see __init__.
    @classmethod
    def from_defaults(cls, *args: Any, **kwargs: _GDALOption) -> Self: ...
    def credentialize(self) -> None: ...
    def aws_creds_from_context_options(self) -> dict[str, str]: ...
    def drivers(self) -> dict[str, str]: ...
    def __enter__(self) -> Self: ...
    def __exit__(
        self,
        exc_type: type[BaseException] | None = None,
        exc_val: BaseException | None = None,
        exc_tb: TracebackType | None = None,
    ) -> None: ...

def defenv(**options: _GDALOption) -> None: ...
def getenv() -> dict[str, _GDALOption]: ...
def hasenv() -> bool: ...
def setenv(**options: _GDALOption) -> None: ...
@deprecated("Please use Env.session.hascreds() instead.")
def hascreds() -> bool: ...
def delenv() -> None: ...

class NullContextManager:
    def __init__(self) -> None: ...
    def __enter__(self) -> Self: ...
    def __exit__(self, *args: object) -> None: ...

def env_ctx_if_needed() -> Env | NullContextManager: ...
def ensure_env(f: _F) -> _F: ...
@deprecated("ensure_env_credentialled is a deprecated alias; use ensure_env_with_credentials instead.")
def ensure_env_credentialled(f: _F) -> _F: ...
def ensure_env_with_credentials(f: _F) -> _F: ...
def gdal_version() -> str: ...

class GDALVersion:
    major: int
    minor: int
    patch: int
    def __init__(self, major: int = 0, minor: int = 0, patch: int = 0) -> None: ...
    def __eq__(self, other: object) -> bool: ...
    def __lt__(self, other: GDALVersion) -> bool: ...
    @classmethod
    def parse(cls, input: str | GDALVersion, include_patch: bool = False) -> Self: ...
    @classmethod
    def runtime(cls, include_patch: bool = False) -> Self: ...
    def at_least(self, other: str | GDALVersion, include_patch: bool = False) -> bool: ...

def require_gdal_version(
    version: str | GDALVersion,
    param: str | None = None,
    # `values` are matched against the decorated function's `param` argument; types depend on that argument.
    values: Iterable[Any] | None = None,
    is_max_version: bool = False,
    reason: str = "",
) -> Callable[[_F], _F]: ...

path: Final[str | None]
