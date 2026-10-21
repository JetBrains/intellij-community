from typing import TypeVar

_K = TypeVar("_K")

def prepare_revoke_token_request(
    token: str, token_type_hint: str | None = None, body: str | None = None, headers: dict[str, _K] | None = None
) -> tuple[str, dict[str, _K | str]]: ...
