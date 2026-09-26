from collections.abc import Collection, Sequence

def prepare_grant_uri(
    uri: str,
    client_id: str,
    response_type: str,
    redirect_uri: str | None = None,
    scope: Collection[str] | str | None = None,
    state: str | None = None,
    **kwargs: str | Sequence[str | None] | None,
): ...
def prepare_token_request(
    grant_type: str,
    body: str = "",
    redirect_uri: str | None = None,
    *,
    scope: Collection[str] | str | None = None,
    code: str | None = None,
    **kwargs: str | None,
) -> str: ...
def parse_authorization_code_response(uri: str, state: str | None = None) -> dict[str, str]: ...
def parse_implicit_response(uri: str, state: str | None = None) -> dict[str, str]: ...
