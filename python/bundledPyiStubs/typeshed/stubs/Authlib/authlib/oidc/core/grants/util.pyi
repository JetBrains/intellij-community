from _typeshed import ReadableBuffer
from collections.abc import Collection, Iterable
from typing import Literal, SupportsBytes, SupportsIndex, overload

from authlib.oidc.core import UserInfo

def is_openid_scope(scope: Collection[str] | str | None) -> bool: ...
def validate_request_prompt(grant, redirect_uri: str, redirect_fragment: bool = False): ...
def validate_nonce(request, exists_nonce, required: bool = False): ...
def generate_id_token(
    token: dict[str, str | int],
    user_info: UserInfo,
    key: str,
    iss: str,
    aud: list[str],
    alg: str = "RS256",
    exp: int = 3600,
    nonce: str | None = None,
    auth_time: int | None = None,
    acr: str | None = None,
    amr: list[str] | None = None,
    code: str | bytes | float | Iterable[SupportsIndex] | SupportsIndex | SupportsBytes | ReadableBuffer | None = None,
    kid: str | None = None,
) -> str: ...

@overload
def create_response_mode_response(
    redirect_uri: str, params: Iterable[tuple[str, str]], response_mode: Literal["form_post"]
) -> tuple[Literal[200], str, list[tuple[str, str]]]: ...
@overload  # `params` can accept dict in another mode
def create_response_mode_response(
    redirect_uri: str, params: Iterable[tuple[str, str]] | dict[str, str], response_mode: Literal["query", "fragment"]
) -> tuple[Literal[302], str, list[tuple[str, str]]]: ...
