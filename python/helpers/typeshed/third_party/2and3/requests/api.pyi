# Stubs for requests.api (Python 3)

import sys
from typing import Optional, Union, Any, Iterable, Mapping, MutableMapping, Tuple, IO, Text

from .models import Response

if sys.version_info >= (3,):
    _Text = str
else:
    _Text = Union[str, Text]

_ParamsMappingValueType = Union[_Text, bytes, int, float, Iterable[Union[_Text, bytes, int, float]]]
_Data = Union[
    None,
    bytes,
    MutableMapping[str, str],
    MutableMapping[str, Text],
    MutableMapping[Text, str],
    MutableMapping[Text, Text],
    Iterable[Tuple[_Text, _Text]],
    IO
]

def request(method: str, url: str, **kwargs) -> Response: ...
def get(url: Union[_Text, bytes],
        params: Optional[
            Union[Mapping[Union[_Text, bytes, int, float], _ParamsMappingValueType],
                  Union[_Text, bytes],
                  Tuple[Union[_Text, bytes, int, float], _ParamsMappingValueType],
                  Mapping[_Text, _ParamsMappingValueType],
                  Mapping[bytes, _ParamsMappingValueType],
                  Mapping[int, _ParamsMappingValueType],
                  Mapping[float, _ParamsMappingValueType]]] = ...,
        **kwargs) -> Response: ...
def options(url: _Text, **kwargs) -> Response: ...
def head(url: _Text, **kwargs) -> Response: ...
def post(url: _Text, data: _Data=..., json=..., **kwargs) -> Response: ...
def put(url: _Text, data: _Data=..., json=..., **kwargs) -> Response: ...
def patch(url: _Text, data: _Data=..., json=..., **kwargs) -> Response: ...
def delete(url: _Text, **kwargs) -> Response: ...
