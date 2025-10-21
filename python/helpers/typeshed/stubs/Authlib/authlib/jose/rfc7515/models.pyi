from _typeshed import Incomplete

class JWSAlgorithm:
    name: Incomplete
    description: Incomplete
    algorithm_type: str
    algorithm_location: str
    def prepare_key(self, raw_data) -> None: ...
    def sign(self, msg, key): ...
    def verify(self, msg, sig, key) -> bool: ...

class JWSHeader(dict[str, object]):
    protected: Incomplete
    header: Incomplete
    def __init__(self, protected, header) -> None: ...
    @classmethod
    def from_dict(cls, obj): ...

class JWSObject(dict[str, object]):
    header: Incomplete
    payload: Incomplete
    type: Incomplete
    def __init__(self, header, payload, type: str = "compact") -> None: ...
    @property
    def headers(self): ...
