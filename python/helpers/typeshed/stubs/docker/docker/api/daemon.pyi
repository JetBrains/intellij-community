from _typeshed import Incomplete
from datetime import datetime
from typing import Any

from docker.types.daemon import CancellableStream

class DaemonApiMixin:
    def df(self) -> dict[str, Any]: ...
    def events(
        self,
        since: datetime | int | None = None,
        until: datetime | int | None = None,
        filters: dict[str, Any] | None = None,
        decode: bool | None = None,
    ) -> CancellableStream[Incomplete]: ...
    def info(self) -> dict[str, Any]: ...
    def login(
        self,
        username: str,
        password: str | None = None,
        email: str | None = None,
        registry: str | None = None,
        reauth: bool = False,
        dockercfg_path: str | None = None,
    ) -> dict[str, Any]: ...
    def ping(self) -> bool: ...
    def version(self, api_version: bool = True) -> dict[str, Any]: ...
