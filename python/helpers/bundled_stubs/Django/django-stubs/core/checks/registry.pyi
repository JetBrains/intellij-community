from collections.abc import Callable, Iterable, Sequence
from typing import Any, Protocol, TypeVar, overload, type_check_only

from django.apps.config import AppConfig
from django.core.checks.messages import CheckMessage

class Tags:
    admin: str
    async_support: str
    caches: str
    compatibility: str
    database: str
    files: str
    models: str
    security: str
    signals: str
    sites: str
    staticfiles: str
    templates: str
    translation: str
    urls: str

@type_check_only
class _CheckCallable(Protocol):
    def __call__(
        self,
        *,
        app_configs: Sequence[AppConfig] | None,
        databases: Sequence[str] | None,
        **kwargs: Any,
    ) -> Iterable[CheckMessage]: ...

_C = TypeVar("_C", bound=_CheckCallable)

@type_check_only
class _ProcessedCheckCallable(Protocol[_C]):
    tags: Sequence[str]
    __call__: _C

class CheckRegistry:
    registered_checks: set[_ProcessedCheckCallable]
    deployment_checks: set[_ProcessedCheckCallable]
    def __init__(self) -> None: ...
    @overload
    def register(self, check: _C, *tags: str, **kwargs: Any) -> _ProcessedCheckCallable[_C]: ...
    @overload
    def register(
        self, check: str | None = None, *tags: str, **kwargs: Any
    ) -> Callable[[_C], _ProcessedCheckCallable[_C]]: ...
    def run_checks(
        self,
        app_configs: Sequence[AppConfig] | None = None,
        tags: Sequence[str] | None = None,
        include_deployment_checks: bool = False,
        databases: Sequence[str] | None = None,
    ) -> list[CheckMessage]: ...
    def tag_exists(self, tag: str, include_deployment_checks: bool = False) -> bool: ...
    def tags_available(self, deployment_checks: bool = False) -> set[str]: ...
    def get_checks(self, include_deployment_checks: bool = False) -> list[_ProcessedCheckCallable]: ...

registry: CheckRegistry = ...
register = registry.register
run_checks = registry.run_checks
tag_exists = registry.tag_exists
