from _typeshed import Incomplete
from typing import Final

import httpx2
from httpx2._types import HeaderTypes, RequestContent

HTTPX_CLIENT_KWARGS: Final[list[str]]

Request = httpx2.Request

def extract_client_kwargs(kwargs: dict[str, Incomplete]) -> dict[str, Incomplete]: ...
def build_request(
    url: httpx2.URL | str, headers: HeaderTypes | None, body: RequestContent, initial_request: httpx2.Request
) -> httpx2.Request: ...
