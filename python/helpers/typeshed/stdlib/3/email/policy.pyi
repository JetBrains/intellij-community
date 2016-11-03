# Stubs for email.policy (Python 3.4)

from typing import Any, Optional, Tuple, Union, Callable
import sys
from email.message import Message
from email.errors import MessageDefect
from email.header import Header
if sys.version_info >= (3, 4):
    from email.contentmanager import ContentManager
from abc import abstractmethod

if sys.version_info >= (3, 3):

    class Policy:
        max_line_length = ...  # type: Optional[int]
        linesep = ...  # type: str
        cte_type = ...  # type: str
        raise_on_defect = ...  # type: bool
        if sys.version_info >= (3, 5):
            mange_from = ...  # type: bool
        def __init__(**kw: Any) -> None: ...
        def clone(**kw: Any) -> 'Policy': ...
        def handle_defect(self, obj: Message,
                          defect: MessageDefect) -> None: ...
        def register_defect(self, obj: Message,
                            defect: MessageDefect) -> None: ...
        def header_max_count(self, name: str) -> Optional[int]: ...
        @abstractmethod
        def header_source_parse(self, sourcelines: List[str]) -> str: ...
        @abstractmethod
        def header_store_parse(self, name: str,
                               value: str) -> Tuple[str, str]: ...
        @abstractmethod
        def header_fetch_parse(self, name: str,
                               value: str) -> str: ...
        @abstractmethod
        def fold(self, name: str, value: str) -> str: ...
        @abstractmethod
        def fold_binary(self, name: str, value: str) -> bytes: ...

    class Compat32(Policy):
        def header_source_parse(self, sourcelines: List[str]) -> str: ...
        def header_store_parse(self, name: str,
                               value: str) -> Tuple[str, str]: ...
        def header_fetch_parse(self, name: str,  # type: ignore
                               value: str) -> Union[str, Header]: ...
        def fold(self, name: str, value: str) -> str: ...
        def fold_binary(self, name: str, value: str) -> bytes: ...

    compat32 = ...  # type: Compat32

    class EmailPolicy(Policy):
        utf8 = ...  # type: bool
        refold_source = ...  # type: str
        header_factory = ...  # type: Callable[[str, str], str]
        if sys.version_info >= (3, 4):
            content_manager = ...  # type: ContentManager
        def header_source_parse(self, sourcelines: List[str]) -> str: ...
        def header_store_parse(self, name: str,
                               value: str) -> Tuple[str, str]: ...
        def header_fetch_parse(self, name: str, value: str) -> str: ...
        def fold(self, name: str, value: str) -> str: ...
        def fold_binary(self, name: str, value: str) -> bytes: ...

    default = ...  # type: EmailPolicy
    SMTP = ...  # type: EmailPolicy
    SMTPUTF8 = ...  # type: EmailPolicy
    HTTP = ...  # type: EmailPolicy
    strict = ...  # type: EmailPolicy
