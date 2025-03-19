from collections.abc import Callable
from typing import Any, Generic, Protocol, TypeVar
from typing_extensions import Self

class _HasId(Protocol):
    @property
    def id(self) -> str | None: ...

_R = TypeVar("_R", default=Any)
_T = TypeVar("_T", bound=_HasId)

class _Page(Generic[_T]):
    has_next: bool
    values: list[_T]
    next_after: str | None

    def __init__(self, values: list[_T], has_next: bool, next_after: str | None) -> None: ...
    @staticmethod
    def empty() -> _Page[_T]: ...
    @staticmethod
    def initial(after: str | None) -> _Page[_T]: ...

class _PageIterator(Generic[_T]):
    page: _Page[_T]
    get_next_page: Callable[[_Page[_T]], _Page[_T]]

    def __init__(self, page: _Page[_T], get_next_page: Callable[[_Page[_T]], _Page[_T]]) -> None: ...
    def __iter__(self) -> Self: ...
    def __next__(self) -> _T: ...

class _Paginated(Generic[_T, _R]):
    paginated_getter: Callable[..., _R]  # Gets passed additional kwargs to find_iter().
    pluck_page_resources_from_response: Callable[[_R], list[_T]]
    def __init__(
        self, paginated_getter: Callable[..., _R], pluck_page_resources_from_response: Callable[[_R], list[_T]]
    ) -> None: ...
    def find_iter(self, *, after: str | None = None, **kwargs: Any) -> _PageIterator[_T]: ...
