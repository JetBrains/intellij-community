from collections import OrderedDict
from collections.abc import Mapping
from typing import Any, overload

@overload
def alphabetize_attributes(attrs: None) -> None: ...
@overload
def alphabetize_attributes(attrs: Mapping[Any, str]) -> OrderedDict[Any, str]: ...
