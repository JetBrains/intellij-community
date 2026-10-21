from collections import defaultdict
from functools import cached_property

from authlib.oauth2.rfc6749 import JsonPayload, JsonRequest, OAuth2Payload, OAuth2Request
from flask.wrappers import Request
from werkzeug.datastructures.structures import CombinedMultiDict, ImmutableMultiDict, MultiDict

class FlaskOAuth2Payload(OAuth2Payload):
    def __init__(self, request: Request) -> None: ...
    @property
    def data(self) -> CombinedMultiDict[str, str]: ...
    @cached_property
    def datalist(self) -> defaultdict[str, list[str]]: ...

class FlaskOAuth2Request(OAuth2Request):
    payload: FlaskOAuth2Payload
    def __init__(self, request: Request) -> None: ...
    @property
    def args(self) -> MultiDict[str, str]: ...  # type: ignore[override]
    @property
    def form(self) -> ImmutableMultiDict[str, str]: ...

class FlaskJsonPayload(JsonPayload):
    def __init__(self, request: Request) -> None: ...
    @property
    def data(self): ...

class FlaskJsonRequest(JsonRequest):
    payload: FlaskJsonPayload
    def __init__(self, request: Request) -> None: ...
