from typing import NamedTuple

from .. import exceptions

LocationParseError = exceptions.LocationParseError

url_attrs: list[str]

class _UrlBase(NamedTuple):
    auth: str | None
    fragment: str | None
    host: str | None
    path: str | None
    port: str | None
    query: str | None
    scheme: str | None

class Url(_UrlBase):
    def __new__(
        cls,
        scheme: str | None = ...,
        auth: str | None = ...,
        host: str | None = ...,
        port: str | None = ...,
        path: str | None = ...,
        query: str | None = ...,
        fragment: str | None = ...,
    ): ...
    @property
    def hostname(self) -> str | None: ...
    @property
    def request_uri(self) -> str: ...
    @property
    def netloc(self) -> str | None: ...
    @property
    def url(self) -> str: ...

def split_first(s: str, delims: str) -> tuple[str, str, str | None]: ...
def parse_url(url: str) -> Url: ...
def get_host(url: str) -> tuple[str, str | None, str | None]: ...
