from collections.abc import Callable, Mapping
from typing import Any, Union
from typing_extensions import TypeAlias

_FieldValue: TypeAlias = str | bytes
_FieldValueTuple: TypeAlias = Union[_FieldValue, tuple[str, _FieldValue], tuple[str, _FieldValue, str]]

def guess_content_type(filename: str | None, default: str = ...) -> str: ...
def format_header_param_rfc2231(name: str, value: _FieldValue) -> str: ...
def format_header_param_html5(name: str, value: _FieldValue) -> str: ...

format_header_param = format_header_param_html5

class RequestField:
    data: Any
    headers: Any
    def __init__(
        self,
        name: str,
        data: _FieldValue,
        filename: str | None = ...,
        headers: Mapping[str, str] | None = ...,
        header_formatter: Callable[[str, _FieldValue], str] = ...,
    ) -> None: ...
    @classmethod
    def from_tuples(
        cls, fieldname: str, value: _FieldValueTuple, header_formatter: Callable[[str, _FieldValue], str] = ...
    ) -> RequestField: ...
    def render_headers(self) -> str: ...
    def make_multipart(
        self, content_disposition: str | None = ..., content_type: str | None = ..., content_location: str | None = ...
    ) -> None: ...
