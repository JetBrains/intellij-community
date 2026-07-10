import re
from _typeshed.wsgi import WSGIApplication
from collections.abc import Callable, Mapping, Sequence
from typing import Any, Literal, TypeAlias, TypedDict, overload, type_check_only
from typing_extensions import Unpack
from xml.etree import ElementTree

from bs4 import BeautifulSoup
from webob import Response
from webtest.app import TestApp, TestRequest, _Files
from webtest.forms import Form

_Pattern: TypeAlias = str | bytes | re.Pattern[str] | Callable[[str], bool]
# NOTE: These are optional dependencies, so we don't want to depend on them
#       in the stubs either. Also there are no stubs for pyquery anyways.
_PyQuery: TypeAlias = Any
_PyQueryParams: TypeAlias = Any
_LxmlElement: TypeAlias = Any

@type_check_only
class _GetParams(TypedDict, total=False):
    params: Mapping[str, str] | str
    headers: Mapping[str, str]
    extra_environ: Mapping[str, Any]
    status: int | str | None
    expect_errors: bool
    xhr: bool

@type_check_only
class _PostParams(_GetParams, total=False):
    upload_files: _Files
    content_type: str

class TestResponse(Response):
    # NOTE: The way WebTest creates reponses the request is always set
    #       we could've used `MaybeNone`, but it seems more pragmatic
    #       to just assume that this is always set.
    request: TestRequest  # type: ignore[assignment]
    app: WSGIApplication
    test_app: TestApp
    parser_features: str | Sequence[str]
    __test__: Literal[False]
    @property
    def forms(self) -> dict[str | int, Form]: ...
    @property
    def form(self) -> Form: ...
    @property
    def testbody(self) -> str: ...
    def follow(self, **kw: Unpack[_GetParams]) -> TestResponse: ...
    def maybe_follow(self, **kw: Unpack[_GetParams]) -> TestResponse: ...
    def click(
        self,
        description: _Pattern | None = None,
        linkid: _Pattern | None = None,
        href: _Pattern | None = None,
        index: int | None = None,
        verbose: bool = False,
        extra_environ: dict[str, Any] | None = None,
    ) -> TestResponse: ...
    def clickbutton(
        self,
        description: _Pattern | None = None,
        buttonid: _Pattern | None = None,
        href: _Pattern | None = None,
        onclick: str | None = None,
        index: int | None = None,
        verbose: bool = False,
    ) -> TestResponse: ...

    @overload
    def goto(self, href: str, method: Literal["get"] = "get", **args: Unpack[_GetParams]) -> TestResponse: ...
    @overload
    def goto(self, href: str, method: Literal["post"], **args: Unpack[_PostParams]) -> TestResponse: ...

    @property
    def normal_body(self) -> bytes: ...
    @property
    def unicode_normal_body(self) -> str: ...
    def __contains__(self, s: str) -> bool: ...
    def mustcontain(self, *strings: str, no: Sequence[str] | str = ...) -> None: ...
    @property
    def html(self) -> BeautifulSoup: ...
    @property
    def xml(self) -> ElementTree.Element: ...
    @property
    def lxml(self) -> _LxmlElement: ...
    @property
    def json(self) -> Any: ...
    @property
    def pyquery(self) -> _PyQuery: ...
    def PyQuery(self, **kwargs: _PyQueryParams) -> _PyQuery: ...
    def showbrowser(self) -> None: ...
    def __str__(self) -> str: ...  # type: ignore[override]  # noqa: Y029
