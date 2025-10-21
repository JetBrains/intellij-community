from __future__ import annotations

from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from collections.abc import Iterable
from typing_extensions import assert_type

from webob.dec import _AnyResponse, wsgify
from webob.request import Request


class App:
    @wsgify
    def __call__(self, request: Request) -> str:
        return "hello"


env: WSGIEnvironment = {}
start_response: StartResponse = lambda x, y, z=None: lambda b: None
application: WSGIApplication = lambda e, s: [b""]
request: Request = Request(env)

x = App()
# since we wsgified our __call__ we should now be a valid WSGIApplication
application = x
assert_type(x(env, start_response), "Iterable[bytes]")
# currently we lose the exact response type, but that should be fine in
# most use-cases, since middlewares operate on an application level, not
# on these raw intermediary functions
assert_type(x(request), _AnyResponse)

# accessing the method from the class should work as you expect it to
assert_type(App.__call__(x, env, start_response), "Iterable[bytes]")
assert_type(App.__call__(x, request), _AnyResponse)


# but we can also wrap it with a middleware that expects to deal with requests
class Middleware:
    @wsgify.middleware
    def restrict_ip(self, req: Request, app: WSGIApplication, ips: list[str]) -> WSGIApplication:
        return app

    __call__ = restrict_ip(x, ips=["127.0.0.1"])


# and we still end up with a valid WSGIApplication
m = Middleware()
application = m
assert_type(m(env, start_response), "Iterable[bytes]")
assert_type(m(request), _AnyResponse)


# the same should work with plain functions
@wsgify
def app(request: Request) -> str:
    return "hello"


application = app
assert_type(app, "wsgify[[], Request]")
assert_type(app(env, start_response), "Iterable[bytes]")
assert_type(app(request), _AnyResponse)
assert_type(app(application), "wsgify[[], Request]")
application = app(application)


@wsgify.middleware
def restrict_ip(req: Request, app: WSGIApplication, ips: list[str]) -> WSGIApplication:
    return app


@restrict_ip(ips=["127.0.0.1"])
@wsgify
def m_app(request: Request) -> str:
    return "hello"


application = m_app
assert_type(m_app, "wsgify[[WSGIApplication], Request]")
assert_type(m_app(env, start_response), "Iterable[bytes]")
assert_type(m_app(request), _AnyResponse)
assert_type(m_app(application), "wsgify[[WSGIApplication], Request]")
application = m_app(application)


# custom request
class MyRequest(Request):
    pass


@wsgify(RequestClass=MyRequest)
def my_request_app(request: MyRequest) -> None:
    pass


application = my_request_app
assert_type(my_request_app, "wsgify[[], MyRequest]")


# we are allowed to accept a less specific request class
@wsgify(RequestClass=MyRequest)
def valid_request_app(request: Request) -> None:
    pass


# but the opposite is not allowed
@wsgify  # type: ignore
def invalid_request_app(request: MyRequest) -> None:
    pass


# we can't really make passing extra arguments directly work
# otherwise we have to give up most of our type safety for
# something that should only be used through wsgify.middleware
wsgify(args=(1,))  # type: ignore
wsgify(kwargs={"ips": ["127.0.0.1"]})  # type: ignore
