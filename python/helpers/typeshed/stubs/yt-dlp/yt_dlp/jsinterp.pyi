import collections
import types
from collections.abc import Callable, Collection, Iterable, Mapping
from typing import Any, NoReturn
from typing_extensions import TypeAlias

from yt_dlp.extractor.common import InfoExtractor
from yt_dlp.utils._utils import function_with_repr

from .utils import ExtractorError

def js_number_to_string(val: float, radix: int = 10) -> str: ...

class JS_Undefined: ...

class JS_Break(ExtractorError):
    def __init__(self) -> None: ...

class JS_Continue(ExtractorError):
    def __init__(self) -> None: ...

class JS_Throw(ExtractorError):
    error: BaseException
    def __init__(self, e: BaseException) -> None: ...

class LocalNameSpace(collections.ChainMap[str, Any]):
    def __setitem__(self, key: str, value: Any) -> None: ...
    def __delitem__(self, key: str) -> NoReturn: ...
    def set_local(self, key: str, value: Any) -> None: ...
    def get_local(self, key: str) -> Any: ...

class Debugger:
    ENABLED: bool
    @staticmethod
    def write(*args: str, level: int = 100) -> None: ...
    @classmethod
    # Callable[[Debugger, str, Any, int, ...], tuple[Any, bool]] but it also accepts *args, **kwargs.
    def wrap_interpreter(cls, f: Callable[..., tuple[Any, bool]]) -> Callable[..., tuple[Any, bool]]: ...

_BuildFunctionReturnType: TypeAlias = Callable[[Collection[Any], Mapping[str, Any], int], Any | None]

class JSInterpreter:
    def __init__(self, code: str, objects: Mapping[str, Any] | None = None) -> None: ...

    class Exception(ExtractorError):
        def __init__(
            self,
            msg: str,
            expr: str | None = None,
            tb: types.TracebackType | None = None,
            expected: bool = False,
            cause: Exception | str | None = None,
            video_id: str | None = None,
            ie: InfoExtractor | None = None,
        ) -> None: ...

    # After wrapping, *args and **kwargs are added but do nothing for this method.
    def interpret_statement(
        self, stmt: str, local_vars: Mapping[str, Any], allow_recursion: int, *args: Any, **kwargs: Any
    ) -> tuple[Any, bool]: ...
    def interpret_expression(self, expr: str, local_vars: Mapping[str, Any], allow_recursion: int) -> Any: ...
    def extract_object(self, objname: str, *global_stack: Iterable[dict[str, Any]]) -> Any: ...
    def extract_function_code(self, funcname: str) -> tuple[list[str], tuple[str, str]]: ...
    def extract_function(self, funcname: str, *global_stack: Iterable[dict[str, Any]]) -> function_with_repr[Any]: ...
    def extract_function_from_code(
        self, argnames: Collection[str], code: str, *global_stack: Iterable[dict[str, Any]]
    ) -> _BuildFunctionReturnType: ...
    # args are passed to the extracted function.
    def call_function(self, funcname: str, *args: Any) -> function_with_repr[Any]: ...
    def build_function(
        self, argnames: Collection[str], code: str, *global_stack: Iterable[dict[str, Any]]
    ) -> _BuildFunctionReturnType: ...
