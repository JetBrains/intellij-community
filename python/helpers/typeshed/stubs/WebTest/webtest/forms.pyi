from collections.abc import Collection, Generator, Iterable, Sequence
from typing import Any, TypeAlias, TypedDict, TypeVar, overload, type_check_only

from bs4 import BeautifulSoup
from webtest.response import TestResponse

_T = TypeVar("_T")

@type_check_only
class _Classes(TypedDict):
    submit: type[Submit]
    button: type[Submit]
    image: type[Submit]
    multiple_select: type[MultipleSelect]
    select: type[Select]
    hidden: type[Hidden]
    file: type[File]
    text: type[Text]
    search: type[Text]
    email: type[Email]
    password: type[Text]
    checkbox: type[Checkbox]
    textarea: type[Textarea]
    radio: type[Radio]

# NOTE: It seems unergonomic having to put isinstance checks everywhere
#       in your test code where you're accessing a form field, so we
#       return `Any` for now. What we would really like to use here is
#       `AnyOf`, but that doesn't exist yet.
_AnyField: TypeAlias = Any

class NoValue: ...

class Upload:
    filename: str
    content: bytes | None
    content_type: str | None
    def __init__(self, filename: str, content: bytes | None = None, content_type: str | None = None) -> None: ...
    def __iter__(self) -> Generator[str | bytes]: ...

class Field:
    classes: _Classes
    form: Form
    tag: str
    name: str
    pos: int
    id: str
    attrs: dict[str, str]
    def __init__(
        self, form: Form, tag: str, name: str, pos: int, value: str | None = None, id: str | None = None, **attrs: str
    ) -> None: ...
    def value__get(self) -> str: ...
    def value__set(self, value: str | None) -> None: ...

    @property
    def value(self) -> str: ...
    @value.setter
    def value(self, value: str | None) -> None: ...

    def force_value(self, value: str | None) -> None: ...

class Select(Field):
    options: list[tuple[str, bool, str]]
    optionPositions: list[int]
    selectedIndex: int | None

    # NOTE: Even though it's safe to pass any object into text, I don't
    #       think that follows the spirit of this argument and is more
    #       likely a consequence of reusing the same utility function
    #       in order to handle bytes for Py2 compat.
    @overload
    def select(self, value: None, text: str | bytes) -> None: ...
    @overload
    def select(self, value: None = None, *, text: str | bytes) -> None: ...
    @overload
    def select(self, value: object, text: None = None) -> None: ...

    def value__get(self) -> str: ...
    def value__set(self, value: object | None) -> None: ...

    @property
    def value(self) -> str: ...
    @value.setter
    def value(self, value: object | None) -> None: ...

class MultipleSelect(Field):
    options: list[tuple[str, bool, str]]
    selectedIndices: list[int]

    @overload
    def select_multiple(self, value: None, texts: Iterable[str | bytes]) -> None: ...
    @overload
    def select_multiple(self, value: None = None, *, texts: Iterable[str | bytes]) -> None: ...
    @overload
    def select_multiple(self, value: Collection[object], texts: None = None) -> None: ...

    def value__get(self) -> list[str] | None: ...  # type: ignore[override]
    def value__set(self, values: Collection[object] | None) -> None: ...

    @property  # type: ignore[override]
    def value(self) -> list[str] | None: ...
    @value.setter
    def value(self, value: Collection[object] | None) -> None: ...

    # NOTE: Since unlike setting the value normally this doesn't perform
    #       any kind of type conversion, we're better off only allowing
    #       what `value__get` is supposed to be able to return.
    def force_value(self, values: list[str] | None) -> None: ...  # type: ignore[override]

class Radio(Select): ...

class Checkbox(Field):
    def value__get(self) -> str | None: ...  # type: ignore[override]
    def value__set(self, value: object) -> None: ...

    @property  # type: ignore[override]
    def value(self) -> str | None: ...
    @value.setter
    def value(self, value: object) -> None: ...

    def checked__get(self) -> bool: ...
    def checked__set(self, value: object) -> None: ...

    @property
    def checked(self) -> bool: ...
    @checked.setter
    def checked(self, value: object) -> None: ...

class Text(Field): ...
class Email(Field): ...
class File(Field): ...
class Textarea(Text): ...
class Hidden(Text): ...

class Submit(Field):
    def value__get(self) -> None: ...  # type: ignore[override]
    @property  # type: ignore[misc]
    def value(self) -> None: ...  # type: ignore[override]
    def value_if_submitted(self) -> str: ...

class Form:
    FieldClass: type[Field]
    response: TestResponse
    text: str
    html: BeautifulSoup
    action: str
    method: str
    id: str | None
    enctype: str
    field_order: list[tuple[str, Field]]
    fields: dict[str, list[Field]]
    def __init__(self, response: TestResponse, text: str, parser_features: Sequence[str] | str = "html.parser") -> None: ...
    # NOTE: Technically it is only safe to pass `str | None` for most fields
    #       but this method is not really usable if we don't lift this
    #       restriction, we just have to assume people know what they
    #       are doing
    def __setitem__(self, name: str, value: Any | None) -> None: ...
    def set(self, name: str, value: Any | None, index: int | None = None) -> None: ...
    def __getitem__(self, name: str) -> _AnyField: ...

    @overload
    def get(self, name: str, index: int | None = None) -> _AnyField: ...
    @overload
    def get(self, name: str, index: int | None, default: _T) -> _AnyField | _T: ...
    @overload
    def get(self, name: str, index: int | None = None, *, default: _T) -> _AnyField | _T: ...

    @overload
    def select(self, name: str, value: None, text: str | bytes, index: int | None = None) -> None: ...
    @overload
    def select(self, name: str, value: None = None, *, text: str | bytes, index: int | None = None) -> None: ...
    @overload
    def select(self, name: str, value: object, text: None = None, index: int | None = None) -> None: ...

    @overload
    def select_multiple(self, name: str, value: None, texts: Iterable[str | bytes], index: int | None = None) -> None: ...
    @overload
    def select_multiple(
        self, name: str, value: None = None, *, texts: Iterable[str | bytes], index: int | None = None
    ) -> None: ...
    @overload
    def select_multiple(self, name: str, value: Iterable[object], texts: None = None, index: int | None = None) -> None: ...

    def submit(
        self, name: str | None = None, index: int | None = None, value: str | None = None, **args: Any
    ) -> TestResponse: ...
    def lint(self) -> None: ...
    def upload_fields(self) -> list[tuple[str, str] | tuple[str, str, bytes]]: ...
    def submit_fields(
        self, name: str | None = None, index: int | None = None, submit_value: str | None = None
    ) -> list[tuple[str, str]]: ...
