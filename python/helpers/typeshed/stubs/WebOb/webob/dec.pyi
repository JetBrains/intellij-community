from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from collections.abc import Callable, Iterable, Mapping
from typing import Any, Generic, TypeVar, overload
from typing_extensions import Concatenate, Never, ParamSpec, Self, TypeAlias

from webob.request import Request
from webob.response import Response

_AnyResponse: TypeAlias = Response | WSGIApplication | str | None
_S = TypeVar("_S")
_AppT = TypeVar("_AppT", bound=WSGIApplication)
_AppT_contra = TypeVar("_AppT_contra", bound=WSGIApplication, contravariant=True)
_RequestT = TypeVar("_RequestT", bound=Request)
_RequestT_contra = TypeVar("_RequestT_contra", bound=Request, contravariant=True)
_P = ParamSpec("_P")
_P2 = ParamSpec("_P2")

_RequestHandlerCallable: TypeAlias = Callable[Concatenate[_RequestT_contra, _P], _AnyResponse]
_RequestHandlerMethod: TypeAlias = Callable[Concatenate[Any, _RequestT_contra, _P], _AnyResponse]
_MiddlewareCallable: TypeAlias = Callable[Concatenate[_RequestT_contra, _AppT_contra, _P], _AnyResponse]
_MiddlewareMethod: TypeAlias = Callable[Concatenate[Any, _RequestT_contra, _AppT_contra, _P], _AnyResponse]
_RequestHandler: TypeAlias = _RequestHandlerCallable[_RequestT_contra, _P] | _RequestHandlerMethod[_RequestT_contra, _P]
_Middleware: TypeAlias = (
    _MiddlewareCallable[_RequestT_contra, _AppT_contra, _P] | _MiddlewareMethod[_RequestT_contra, _AppT_contra, _P]
)

class wsgify(Generic[_RequestT_contra, _P]):
    RequestClass: type[Request]
    func: _RequestHandler[_RequestT_contra, _P] | None
    args: tuple[Any, ...]
    kwargs: dict[str, Any]
    middleware_wraps: WSGIApplication | None
    # NOTE: We disallow passing args/kwargs using this direct API, because
    #       we can't really make it work as a decorator this way, these
    #       arguments should only really be used indrectly through the
    #       middleware decorator, where we can be more type safe
    @overload
    def __init__(
        self: wsgify[Request, []],
        func: _RequestHandler[Request, []] | None = None,
        RequestClass: None = None,
        args: tuple[()] = (),
        kwargs: None = None,
        middleware_wraps: None = None,
    ) -> None: ...
    @overload
    def __init__(
        self: wsgify[_RequestT_contra, []],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        func: _RequestHandler[_RequestT_contra, []] | None,
        RequestClass: type[_RequestT_contra],
        args: tuple[()] = (),
        kwargs: None = None,
        middleware_wraps: None = None,
    ) -> None: ...
    @overload
    def __init__(
        self: wsgify[_RequestT_contra, []],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        func: _RequestHandler[_RequestT_contra, []] | None = None,
        *,
        RequestClass: type[_RequestT_contra],
        args: tuple[()] = (),
        kwargs: None = None,
        middleware_wraps: None = None,
    ) -> None: ...
    @overload
    def __init__(
        self: wsgify[Request, [_AppT_contra]],
        func: _Middleware[Request, _AppT_contra, []] | None = None,
        RequestClass: None = None,
        args: tuple[()] = (),
        kwargs: None = None,
        *,
        middleware_wraps: _AppT_contra,
    ) -> None: ...
    @overload
    def __init__(
        self: wsgify[_RequestT_contra, [_AppT_contra]],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        func: _Middleware[_RequestT_contra, _AppT_contra, []] | None,
        RequestClass: type[_RequestT_contra],
        args: tuple[()] = (),
        kwargs: None = None,
        *,
        middleware_wraps: _AppT_contra,
    ) -> None: ...
    @overload
    def __init__(
        self: wsgify[_RequestT_contra, [_AppT_contra]],  # pyright: ignore[reportInvalidTypeVarUse]  #11780
        func: _Middleware[_RequestT_contra, _AppT_contra, []] | None = None,
        *,
        RequestClass: type[_RequestT_contra],
        args: tuple[()] = (),
        kwargs: None = None,
        middleware_wraps: _AppT_contra,
    ) -> None: ...
    @overload
    def __get__(self, obj: None, type: type[_S]) -> _unbound_wsgify[_RequestT_contra, _P, _S]: ...
    @overload
    def __get__(self, obj: object, type: type | None = None) -> Self: ...
    @overload
    def __call__(self, env: WSGIEnvironment, /, start_response: StartResponse) -> Iterable[bytes]: ...
    @overload
    def __call__(self, func: _RequestHandler[_RequestT_contra, _P], /) -> Self: ...
    @overload
    def __call__(self, req: _RequestT_contra) -> _AnyResponse: ...
    @overload
    def __call__(self, req: _RequestT_contra, *args: _P.args, **kw: _P.kwargs) -> _AnyResponse: ...
    def get(self, url: str, **kw: Any) -> _AnyResponse: ...
    def post(
        self, url: str, POST: str | bytes | Mapping[Any, Any] | Mapping[Any, list[Any] | tuple[Any, ...]] | None = None, **kw: Any
    ) -> _AnyResponse: ...
    def request(self, url: str, **kw: Any) -> _AnyResponse: ...
    def call_func(self, req: _RequestT_contra, *args: _P.args, **kwargs: _P.kwargs) -> _AnyResponse: ...
    # technically this could bind different type vars, but we disallow it for safety
    def clone(self, func: _RequestHandler[_RequestT_contra, _P] | None = None, **kw: Never) -> Self: ...
    @property
    def undecorated(self) -> _RequestHandler[_RequestT_contra, _P] | None: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: None = None, app: None | _AppT = None, *_: _P.args, **kw: _P.kwargs
    ) -> _UnboundMiddleware[Any, _AppT, _P]: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: _MiddlewareCallable[_RequestT, _AppT, _P2], app: None = None
    ) -> _MiddlewareFactory[_RequestT, _AppT, _P2]: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: _MiddlewareMethod[_RequestT, _AppT, _P2], app: None = None
    ) -> _MiddlewareFactory[_RequestT, _AppT, _P2]: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: _MiddlewareMethod[_RequestT, _AppT, _P2], app: None = None, *_: _P2.args, **kw: _P2.kwargs
    ) -> _MiddlewareFactory[_RequestT, _AppT, _P2]: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: _MiddlewareMethod[_RequestT, _AppT, _P2], app: _AppT
    ) -> type[wsgify[_RequestT, Concatenate[_AppT, _P2]]]: ...
    @overload
    @classmethod
    def middleware(
        cls, middle_func: _MiddlewareMethod[_RequestT, _AppT, _P2], app: _AppT, *_: _P2.args, **kw: _P2.kwargs
    ) -> type[wsgify[_RequestT, Concatenate[_AppT, _P2]]]: ...

class _unbound_wsgify(wsgify[_RequestT_contra, _P], Generic[_RequestT_contra, _P, _S]):
    @overload  # type: ignore[override]
    def __call__(self, __self: _S, env: WSGIEnvironment, /, start_response: StartResponse) -> Iterable[bytes]: ...
    @overload
    def __call__(self, __self: _S, func: _RequestHandler[_RequestT_contra, _P], /) -> Self: ...
    @overload
    def __call__(self, __self: _S, /, req: _RequestT_contra) -> _AnyResponse: ...
    @overload
    def __call__(self, __self: _S, /, req: _RequestT_contra, *args: _P.args, **kw: _P.kwargs) -> _AnyResponse: ...

class _UnboundMiddleware(Generic[_RequestT_contra, _AppT_contra, _P]):
    wrapper_class: type[wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]]
    app: _AppT_contra | None
    kw: dict[str, Any]
    def __init__(
        self,
        wrapper_class: type[wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]],
        app: _AppT_contra | None,
        kw: dict[str, Any],
    ) -> None: ...
    @overload
    def __call__(self, func: None, app: _AppT_contra | None = None) -> Self: ...
    @overload
    def __call__(
        self, func: _Middleware[_RequestT_contra, _AppT_contra, _P], app: None = None
    ) -> wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]: ...
    @overload
    def __call__(
        self, func: _Middleware[_RequestT_contra, _AppT_contra, _P], app: _AppT_contra
    ) -> wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]: ...

class _MiddlewareFactory(Generic[_RequestT_contra, _AppT_contra, _P]):
    wrapper_class: type[wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]]
    middleware: _Middleware[_RequestT_contra, _AppT_contra, _P]
    kw: dict[str, Any]
    def __init__(
        self,
        wrapper_class: type[wsgify[_RequestT_contra, Concatenate[_AppT_contra, _P]]],
        middleware: _Middleware[_RequestT_contra, _AppT_contra, _P],
        kw: dict[str, Any],
    ) -> None: ...
    # NOTE: Technically you are not allowed to pass args, but we give up all kinds
    #       of other safety if we don't use ParamSpec
    @overload
    def __call__(
        self, app: None = None, *_: _P.args, **config: _P.kwargs
    ) -> _MiddlewareFactory[_RequestT_contra, _AppT_contra, []]: ...
    @overload
    def __call__(self, app: _AppT_contra, *_: _P.args, **config: _P.kwargs) -> wsgify[_RequestT_contra, [_AppT_contra]]: ...
