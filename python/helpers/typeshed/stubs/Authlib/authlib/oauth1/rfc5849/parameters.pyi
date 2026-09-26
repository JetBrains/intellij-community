from collections.abc import Iterable
from typing import TypeVar

_K = TypeVar("_K")

def prepare_headers(
    oauth_params: Iterable[tuple[str, str]], headers: dict[str, _K] | None = None, realm=None
) -> dict[str, _K | str]: ...
def prepare_form_encoded_body(oauth_params: Iterable[tuple[str, str]], body: Iterable[tuple[str, str]]) -> str: ...
def prepare_request_uri_query(oauth_params: Iterable[tuple[str, str]], uri: str) -> str: ...
