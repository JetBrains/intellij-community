from typing import Mapping, Any, Optional, Union

from . import algorithms

def decode(jwt: Union[str, bytes], key: Union[str, bytes] = ...,
           verify: bool = ..., algorithms: Optional[Any] = ...,
           options: Optional[Mapping[Any, Any]] = ...,
           **kwargs: Any) -> Mapping[str, Any]: ...

def encode(payload: Mapping[str, Any], key: Union[str, bytes],
           algorithm: str = ..., headers: Optional[Mapping[str, Any]] = ...,
           json_encoder: Optional[Any] = ...) -> bytes: ...

def register_algorithm(alg_id: str,
                       alg_obj: algorithms.Algorithm) -> None: ...

def unregister_algorithm(alg_id: str) -> None: ...

class InvalidTokenError(Exception): pass
class DecodeError(InvalidTokenError): pass
class ExpiredSignatureError(InvalidTokenError): pass
class InvalidAudienceError(InvalidTokenError): pass
class InvalidIssuerError(InvalidTokenError): pass
class InvalidIssuedAtError(InvalidTokenError): pass
class ImmatureSignatureError(InvalidTokenError): pass
class InvalidKeyError(Exception): pass
class InvalidAlgorithmError(InvalidTokenError): pass
class MissingRequiredClaimError(InvalidTokenError): ...

# Compatibility aliases (deprecated)
ExpiredSignature = ExpiredSignatureError
InvalidAudience = InvalidAudienceError
InvalidIssuer = InvalidIssuerError

# These aren't actually documented, but the package
# exports them in __init__.py, so we should at least
# make sure that mypy doesn't raise spurious errors
# if they're used.
get_unverified_header = ...  # type: Any
PyJWT = ...  # type: Any
PyJWS = ...  # type: Any
