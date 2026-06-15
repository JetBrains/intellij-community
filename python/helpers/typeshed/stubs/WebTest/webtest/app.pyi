import json
from _typeshed import SupportsItems, SupportsKeysAndGetItem
from _typeshed.wsgi import WSGIApplication, WSGIEnvironment
from collections.abc import Iterable, Sequence
from http.cookiejar import CookieJar, DefaultCookiePolicy
from typing import Any, Generic, Literal, TypeAlias, TypeVar

from webob.request import BaseRequest
from webtest.forms import File, Upload
from webtest.response import TestResponse

# NOTE: While it is possible to pass different kinds of values depending on
#       the exact configuration of the request, it seems more robust to
#       restrict them to the types that are supported by all code paths.
#       I don't expect anyone to try to pass different kinds of values
#       in a non-JSON request.
_ParamValue: TypeAlias = File | Upload | int | bytes | str
_Params: TypeAlias = SupportsItems[str | bytes, _ParamValue] | Sequence[tuple[str | bytes, _ParamValue]]
# NOTE: Using `Collection` rather than `Iterable` would probably be slightly
#       safer since WebTest will check this parameter for truthyness. But since
#       objects are truthy by default, this should only lead to issues in truly
#       exotic cases.
_ExtraEnviron: TypeAlias = SupportsKeysAndGetItem[str, Any] | Iterable[tuple[str, Any]]
_Files: TypeAlias = Sequence[tuple[str, str] | tuple[str, str, bytes]]
_AppT = TypeVar("_AppT", bound=WSGIApplication, default=WSGIApplication)

__all__ = ["TestApp", "TestRequest"]

class AppError(Exception):
    def __init__(self, message: str, *args: object) -> None: ...

class CookiePolicy(DefaultCookiePolicy): ...

class TestRequest(BaseRequest):
    ResponseClass: type[TestResponse]
    __test__: Literal[False]

class TestApp(Generic[_AppT]):
    RequestClass: type[TestRequest]
    app: _AppT
    lint: bool
    relative_to: str | None
    extra_environ: WSGIEnvironment
    use_unicode: bool
    cookiejar: CookieJar
    JSONEncoder: json.JSONEncoder
    __test__: Literal[False]
    def __init__(
        self,
        app: _AppT,
        # NOTE: this extra_environ is different from the others and needs to
        #       support __delitem__, it seems easiest to just treat this like
        #       a regular WSGIEnvironment. The docs also say that this should
        #       be a dictionary.
        extra_environ: WSGIEnvironment | None = None,
        relative_to: str | None = None,
        use_unicode: bool = True,
        cookiejar: CookieJar | None = None,
        parser_features: Sequence[str] | str | None = None,
        json_encoder: json.JSONEncoder | None = None,
        lint: bool = True,
    ) -> None: ...
    def get_authorization(self) -> tuple[str, str | tuple[str, str]]: ...
    def set_authorization(self, value: tuple[str, str | tuple[str, str]]) -> None: ...

    @property
    def authorization(self) -> tuple[str, str | tuple[str, str]]: ...
    @authorization.setter
    def authorization(self, value: tuple[str, str | tuple[str, str]]) -> None: ...

    @property
    def cookies(self) -> dict[str, str | None]: ...
    def set_cookie(self, name: str, value: str | None) -> None: ...
    def reset(self) -> None: ...
    def set_parser_features(self, parser_features: Sequence[str] | str) -> None: ...
    def get(
        self,
        url: str,
        params: _Params | str | None = None,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        xhr: bool = False,
    ) -> TestResponse: ...
    def post(
        self,
        url: str,
        params: _Params | str = "",
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        upload_files: _Files | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def put(
        self,
        url: str,
        params: _Params | str = "",
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        upload_files: _Files | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def patch(
        self,
        url: str,
        params: _Params | str = "",
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        upload_files: _Files | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def delete(
        self,
        url: str,
        params: _Params | str = "",
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def options(
        self,
        url: str,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        xhr: bool = False,
    ) -> TestResponse: ...
    def head(
        self,
        url: str,
        params: _Params | str | None = None,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        xhr: bool = False,
    ) -> TestResponse: ...
    def post_json(
        self,
        url: str,
        params: Any = ...,
        *,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def put_json(
        self,
        url: str,
        params: Any = ...,
        *,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def patch_json(
        self,
        url: str,
        params: Any = ...,
        *,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def delete_json(
        self,
        url: str,
        params: Any = ...,
        *,
        headers: dict[str, str] | None = None,
        extra_environ: _ExtraEnviron | None = None,
        status: int | str | None = None,
        expect_errors: bool = False,
        content_type: str | None = None,
        xhr: bool = False,
    ) -> TestResponse: ...
    def encode_multipart(self, params: Iterable[tuple[str | bytes, _ParamValue]], files: _Files) -> tuple[str, bytes]: ...
    def request(
        self, url_or_req: str | TestRequest, status: int | str | None = None, expect_errors: bool = False, **req_params: Any
    ) -> TestResponse: ...
    def do_request(
        self, req: TestRequest, status: int | str | None = None, expect_errors: bool | None = None
    ) -> TestResponse: ...
