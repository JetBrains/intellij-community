from collections.abc import Callable, Iterable, Iterator, Mapping
from typing import Any, Protocol, TypeAlias, TypeVar, type_check_only

from django.db.models import Choices

_Choice: TypeAlias = tuple[Any, Any]
_ChoiceNamedGroup: TypeAlias = tuple[str, Iterable[_Choice]]
_Choices: TypeAlias = Iterable[_Choice | _ChoiceNamedGroup]
_ChoicesMapping: TypeAlias = Mapping[Any, Any]
_ChoicesInput: TypeAlias = _Choices | _ChoicesMapping | type[Choices] | Callable[[], _Choices | _ChoicesMapping]  # noqa: PYI047

@type_check_only
class _ChoicesCallable(Protocol):
    def __call__(self) -> _Choices: ...

class BaseChoiceIterator:
    def __getitem__(self, index: int) -> _Choice | _ChoiceNamedGroup: ...
    def __iter__(self) -> Iterator[_Choice | _ChoiceNamedGroup]: ...

class BlankChoiceIterator(BaseChoiceIterator):
    choices: _Choices
    blank_choice: _Choices
    def __init__(self, choices: _Choices, blank_choice: _Choices) -> None: ...

class CallableChoiceIterator(BaseChoiceIterator):
    func: _ChoicesCallable
    def __init__(self, func: _ChoicesCallable) -> None: ...

_V = TypeVar("_V")
_L = TypeVar("_L")

def flatten_choices(choices: Iterable[tuple[_V, _L | Iterable[tuple[_V, _L]]]]) -> Iterator[tuple[_V, _L]]: ...
def normalize_choices(value: Any, *, depth: int = 0) -> Any: ...

__all__ = [
    "BaseChoiceIterator",
    "BlankChoiceIterator",
    "CallableChoiceIterator",
    "flatten_choices",
    "normalize_choices",
]
