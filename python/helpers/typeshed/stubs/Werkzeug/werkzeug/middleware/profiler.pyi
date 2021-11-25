from _typeshed.wsgi import StartResponse, WSGIApplication, WSGIEnvironment
from typing import IO, Iterable, Text

class ProfilerMiddleware(object):
    def __init__(
        self,
        app: WSGIApplication,
        stream: IO[str] = ...,
        sort_by: tuple[Text, Text] = ...,
        restrictions: Iterable[str | float] = ...,
        profile_dir: Text | None = ...,
        filename_format: Text = ...,
    ) -> None: ...
    def __call__(self, environ: WSGIEnvironment, start_response: StartResponse) -> list[bytes]: ...
