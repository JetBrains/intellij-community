from _typeshed import Incomplete

from kafka.sasl.abc import SaslMechanism

log: Incomplete

class SaslMechanismAwsMskIam(SaslMechanism):
    host: Incomplete
    def __init__(self, **config) -> None: ...
    def auth_bytes(self): ...
    def receive(self, auth_bytes) -> None: ...
    def is_done(self): ...
    def is_authenticated(self): ...
    def auth_details(self): ...

class AwsMskIamClient:
    UNRESERVED_CHARS: Incomplete
    algorithm: str
    expires: str
    hashfunc: Incomplete
    headers: Incomplete
    version: str
    service: str
    action: Incomplete
    datestamp: Incomplete
    timestamp: Incomplete
    host: Incomplete
    access_key: Incomplete
    secret_key: Incomplete
    region: Incomplete
    token: Incomplete
    def __init__(self, host, access_key, secret_key, region, token=None) -> None: ...
    def first_message(self): ...
