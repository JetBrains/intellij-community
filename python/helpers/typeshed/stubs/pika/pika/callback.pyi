from _typeshed import Incomplete
from collections.abc import Callable, Mapping
from logging import Logger
from typing import Any, Final, Literal, ParamSpec, TypeAlias, TypeVar

from .amqp_object import AMQPObject

_P = ParamSpec("_P")
_R = TypeVar("_R")
AMQPValue: TypeAlias = type[AMQPObject] | AMQPObject | int | str

LOGGER: Logger

def name_or_value(value: AMQPValue) -> str: ...
def sanitize_prefix(function: Callable[_P, _R]) -> Callable[_P, _R]: ...
def check_for_prefix_and_key(function: Callable[_P, _R]) -> Callable[_P, _R | Literal[False]]: ...

class CallbackManager:
    CALLS: Final = "calls"
    ARGUMENTS: Final = "arguments"
    DUPLICATE_WARNING: Final = 'Duplicate callback found for "%s:%s"'
    CALLBACK: Final = "callback"
    ONE_SHOT: Final = "one_shot"
    ONLY_CALLER: Final = "only"
    def __init__(self) -> None: ...
    def add(
        self,
        prefix: str | int,
        key: AMQPValue,
        # Parameter type must match arguments passed to process()
        callback: Callable[..., object],
        one_shot: bool = True,
        only_caller: object | None = None,
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> tuple[str | int, str | object]: ...
    def clear(self) -> None: ...
    def cleanup(self, prefix: str | int) -> bool: ...
    def pending(self, prefix: str | int, key: AMQPValue) -> int | None: ...
    def process(
        self,
        prefix: str | int,
        key: AMQPValue,
        caller,
        *args: Any,  # Arguments depends on callbacks stored on self._stack
        **keywords: Any,
    ) -> bool: ...
    def remove(
        self,
        prefix: str | int,
        key: AMQPValue,
        callback_value: Callable[..., object] | None = None,
        arguments: Mapping[str, Incomplete] | None = None,
    ) -> bool: ...
    def remove_all(self, prefix: str | int, key: AMQPValue) -> Literal[False] | None: ...
