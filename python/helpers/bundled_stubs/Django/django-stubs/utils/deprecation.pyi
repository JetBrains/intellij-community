from collections.abc import Awaitable, Callable, Sequence
from types import FunctionType, MethodType
from typing import Any, ClassVar, Protocol, TypeAlias, type_check_only

from _typeshed import Self as MetaclassSelf  # noqa: TID251
from django.http.request import HttpRequest
from django.http.response import HttpResponseBase
from typing_extensions import TypeVar

_C = TypeVar("_C", bound=Callable[..., Any])

def django_file_prefixes() -> tuple[str, ...]: ...

class RemovedInNextVersionWarning(DeprecationWarning): ...
class RemovedInDjango70Warning(PendingDeprecationWarning): ...

RemovedAfterNextVersionWarning: TypeAlias = RemovedInDjango70Warning

def warn_about_external_use(
    message: str,
    category: type[Warning] | None,
    *,
    skip_name_prefixes: str | tuple[str, ...] | None = None,
    skip_frames: int = 0,
    internal_modules: tuple[str, ...] | None = None,
) -> None: ...
def warn_about_implementation(
    message: str,
    category: type[Warning] | None,
    target: FunctionType | MethodType | classmethod[Any, Any, Any] | property | staticmethod[Any, Any] | type,
) -> None: ...

class warn_about_renamed_method:
    class_name: str
    old_method_name: str
    new_method_name: str
    deprecation_warning: type[DeprecationWarning]
    def __init__(
        self, class_name: str, old_method_name: str, new_method_name: str, deprecation_warning: type[DeprecationWarning]
    ) -> None: ...
    def __call__(self, f: _C) -> _C: ...

class RenameMethodsBase(type):
    renamed_methods: tuple[tuple[str, str, type[DeprecationWarning]], ...]
    def __new__(
        cls: type[MetaclassSelf], name: str, bases: tuple[type, ...], attrs: dict[str, Any]
    ) -> MetaclassSelf: ...

def deprecate_posargs(deprecation_warning: type[Warning], remappable_names: Sequence[str], /) -> Callable[[_C], _C]: ...

@type_check_only
class _AnyGetResponseCallable(Protocol):
    def __call__(self, request: HttpRequest, /) -> HttpResponseBase | Awaitable[HttpResponseBase]: ...

class MiddlewareMixin:
    sync_capable: ClassVar[bool]
    async_capable: ClassVar[bool]

    get_response: _AnyGetResponseCallable
    async_mode: bool
    def __init__(self, get_response: _AnyGetResponseCallable) -> None: ...
    def __call__(self, request: HttpRequest) -> HttpResponseBase | Awaitable[HttpResponseBase]: ...
    async def __acall__(self, request: HttpRequest) -> HttpResponseBase: ...
