# Stubs for email.headerregistry (Python 3.4)

import datetime as dt
import sys
from typing import Dict, Tuple, Optional, Any, Union, Mapping
from email.errors import MessageDefect
if sys.version_info >= (3, 3):
    from email.policy import Policy

if sys.version_info >= (3, 3):

    class BaseHeader(str):
        @property
        def name(self) -> str: ...
        @property
        def defects(self) -> Tuple[MessageDefect, ...]: ...
        @property
        def max_count(self) -> Optional[int]: ...
        def __new__(cls, name: str, value: Any) -> 'BaseHeader': ...
        def init(self, *args: Any, **kw: Any) -> None: ...
        def fold(self, *, policy: Policy) -> str: ...

    class UnstructuredHeader:
        @classmethod
        def parse(cls, string: str, kwds: Dict[str, Any]) -> None: ...

    class UniqueUnstructuredHeader(UnstructuredHeader): ...

    class DateHeader:
        datetime = ...  # type: dt.datetime
        @classmethod
        def parse(cls, string: Union[str, dt.datetime],
                  kwds: Dict[str, Any]) -> None: ...

    class UniqueDateHeader(DateHeader): ...

    class AddressHeader:
        groups = ...  # type: Tuple[Group, ...]
        addresses = ...  # type: Tuple[Address, ...]
        @classmethod
        def parse(cls, string: str, kwds: Dict[str, Any]) -> None: ...

    class UniqueAddressHeader(AddressHeader): ...

    class SingleAddressHeader(AddressHeader):
        @property
        def address(self) -> Address: ...

    class UniqueSingleAddressHeader(SingleAddressHeader): ...

    class MIMEVersionHeader:
        version = ...  # type: Optional[str]
        major = ...  # type: Optional[int]
        minor = ...  # type: Optional[int]
        @classmethod
        def parse(cls, string: str, kwds: Dict[str, Any]) -> None: ...

    class ParameterizedMIMEHeader:
        params = ...  # type: Mapping[str, Any]
        @classmethod
        def parse(cls, string: str, kwds: Dict[str, Any]) -> None: ...

    class ContentTypeHeader(ParameterizedMIMEHeader):
        content_type = ...  # type: str
        maintype = ...  # type: str
        subtype = ...  # type: str

    class ContentDispositionHeader(ParameterizedMIMEHeader):
        content_disposition = ...  # type: str

    class ContentTransferEncoding:
        cte = ...  # type: str
        @classmethod
        def parse(cls, string: str, kwds: Dict[str, Any]) -> None: ...

    class HeaderRegistry:
        def __init__(self, base_class: BaseHeader = ...,
                     default_class: BaseHeader = ...,
                     use_default_map: bool = ...) -> None: ...
        def map_to_type(self, name: str, cls: BaseHeader) -> None: ...
        def __getitem__(self, name: str) -> BaseHeader: ...
        def __call__(self, name: str, value: Any) -> BaseHeader: ...

    class Address:
        display_name = ...  # type: str
        username = ...  # type: str
        domain = ...  # type: str
        @property
        def addr_spec(self) -> str: ...
        def __init__(self, display_name: str = ...,
                     username: Optional[str] = ...,
                     domain: Optional[str] = ...,
                     addr_spec: Optional[str]=...) -> None: ...
        def __str__(self) -> str: ...

    class Group:
        display_name = ...  # type: Optional[str]
        addresses = ...  # type: Tuple[Address, ...]
        def __init__(self, display_name: Optional[str] = ...,
                     addresses: Optional[Tuple[Address, ...]] = ...) \
                     -> None: ...
        def __str__(self) -> str: ...
