import logging
import re
from typing import Final, Protocol, TypeAlias, overload, type_check_only
from typing_extensions import LiteralString

import regex

_Pattern: TypeAlias = re.Pattern[str] | regex.Pattern[str]

__all__ = ["titlecase"]
__version__: Final[str]
logger: Final[logging.Logger]

REGEX_AVAILABLE: Final[bool]

SMALL: Final[str]
PUNCT: Final[str]

SMALL_WORDS: _Pattern
SMALL_FIRST: _Pattern
SMALL_LAST: _Pattern
SUBPHRASE: _Pattern

MAC_MC: Final[_Pattern]
MR_MRS_MS_DR: Final[_Pattern]
INLINE_PERIOD: Final[_Pattern]
UC_ELSEWHERE: Final[_Pattern]
CAPFIRST: Final[_Pattern]
APOS_SECOND: Final[_Pattern]
UC_INITIALS: Final[_Pattern]

@type_check_only
class _CallbackProtocol(Protocol):
    def __call__(self, word: str, /, *, all_caps: bool) -> str | None: ...

class Immutable: ...
class ImmutableString(str, Immutable): ...
class ImmutableBytes(bytes, Immutable): ...

@overload
def set_small_word_list() -> None: ...
@overload
def set_small_word_list(small: str) -> None: ...

def titlecase(
    text: str, callback: _CallbackProtocol | None = None, small_first_last: bool = True, preserve_blank_lines: bool = False
) -> LiteralString: ...
def create_wordlist_filter_from_file(file_path: str | None) -> _CallbackProtocol: ...
def cmd() -> None: ...
