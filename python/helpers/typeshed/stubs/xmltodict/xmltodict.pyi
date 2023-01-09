from _typeshed import SupportsRead, SupportsWrite
from collections import OrderedDict
from collections.abc import Mapping
from typing import Any, overload

class ParsingInterrupted(Exception): ...

def parse(
    xml_input: str | bytes | SupportsRead[bytes],
    encoding: str | None = ...,
    expat: Any = ...,
    process_namespaces: bool = ...,
    namespace_separator: str = ...,
    disable_entities: bool = ...,
    **kwargs: Any,
) -> OrderedDict[str, Any]: ...
@overload
def unparse(
    input_dict: Mapping[str, Any],
    output: SupportsWrite[bytes] | SupportsWrite[str],
    encoding: str = ...,
    full_document: bool = ...,
    short_empty_elements: bool = ...,
    **kwargs: Any,
) -> None: ...
@overload
def unparse(
    input_dict: Mapping[str, Any],
    output: None = ...,
    encoding: str = ...,
    full_document: bool = ...,
    short_empty_elements: bool = ...,
    **kwargs: Any,
) -> str: ...
