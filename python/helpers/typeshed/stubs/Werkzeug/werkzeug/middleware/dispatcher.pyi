from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from typing import Iterable, Mapping, Text

class DispatcherMiddleware(object):
    app: WSGIApplication
    mounts: Mapping[Text, WSGIApplication]
    def __init__(self, app: WSGIApplication, mounts: Mapping[Text, WSGIApplication] | None = ...) -> None: ...
    def __call__(self, environ: WSGIEnvironment, start_response: StartResponse) -> Iterable[bytes]: ...
