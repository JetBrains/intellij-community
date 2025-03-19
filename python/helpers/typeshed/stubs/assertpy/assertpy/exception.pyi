from typing import Any
from typing_extensions import Self

__tracebackhide__: bool

class ExceptionMixin:
    def raises(self, ex: type[BaseException] | BaseException) -> Self: ...
    def when_called_with(self, *some_args: Any, **some_kwargs: dict[str, Any]) -> Self: ...
