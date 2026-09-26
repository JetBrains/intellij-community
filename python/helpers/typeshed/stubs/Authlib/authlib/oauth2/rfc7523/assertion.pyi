def sign_jwt_bearer_assertion(
    key, issuer, audience, subject=None, issued_at=None, expires_at=None, claims=None, header=None, *, alg=None, expires_in=3600
) -> str: ...
def client_secret_jwt_sign(
    client_secret,
    client_id,
    token_endpoint,
    alg: str = "HS256",
    claims=None,
    *,
    issued_at=None,
    expires_at=None,
    header=None,
    expires_in=3600,
) -> str: ...
def private_key_jwt_sign(
    private_key,
    client_id,
    token_endpoint,
    alg: str = "RS256",
    claims=None,
    *,
    issued_at=None,
    expires_at=None,
    header=None,
    expires_in=3600,
) -> str: ...
