from typing import Literal, TypeVar

_K = TypeVar("_K")

def add_to_uri(token: str, uri: str) -> str: ...
def add_to_headers(token: str, headers: dict[str, _K] | None = None) -> dict[str, _K | str]: ...
def add_to_body(token: str, body: str | None = None) -> str: ...
def add_bearer_token(
    token: str,
    uri: str,
    headers: dict[str, _K],
    body: str,
    placement: Literal["uri", "url", "query", "header", "headers", "body"] = "header",
) -> tuple[str, dict[str, _K | str], str]: ...
