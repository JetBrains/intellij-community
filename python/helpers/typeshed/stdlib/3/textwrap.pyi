# Better textwrap stubs hand-written by o11c.
# https://docs.python.org/3/library/textwrap.html
from typing import (
    Callable,
    List,
)

class TextWrapper:
    def __init__(
        self,
        width: int = ...,
        *,
        initial_indent: str = ...,
        subsequent_indent: str = ...,
        expand_tabs: bool = ...,
        tabsize: int = ...,
        replace_whitespace: bool = ...,
        fix_sentence_endings: bool = ...,
        break_long_words: bool = ...,
        break_on_hyphens: bool = ...,
        drop_whitespace: bool = ...,
        max_lines: int = ...,
        placeholder: str = ...
    ) -> None:
        self.width = width
        self.initial_indent = initial_indent
        self.subsequent_indent = subsequent_indent
        self.expand_tabs = expand_tabs
        self.tabsize = tabsize
        self.replace_whitespace = replace_whitespace
        self.fix_sentence_endings = fix_sentence_endings
        self.break_long_words = break_long_words
        self.break_on_hyphens = break_on_hyphens
        self.drop_whitespace = drop_whitespace
        self.max_lines = max_lines
        self.placeholder = placeholder

    # Private methods *are* part of the documented API for subclasses.
    def _munge_whitespace(self, text: str) -> str:
        ...

    def _split(self, text: str) -> List[str]:
        ...

    def _fix_sentence_endings(self, chunks: List[str]) -> None:
        ...

    def _handle_long_word(self, reversed_chunks: List[str], cur_line: List[str], cur_len: int, width: int) -> None:
        ...

    def _wrap_chunks(self, chunks: List[str]) -> List[str]:
        ...

    def _split_chunks(self, text: str) -> List[str]:
        ...

    def wrap(self, text: str) -> List[str]:
        ...

    def fill(self, text: str) -> str:
        ...


def wrap(
        text: str = ...,
        width: int = ...,
        *,
        initial_indent: str = ...,
        subsequent_indent: str = ...,
        expand_tabs: bool = ...,
        tabsize: int = ...,
        replace_whitespace: bool = ...,
        fix_sentence_endings: bool = ...,
        break_long_words: bool = ...,
        break_on_hyphens: bool = ...,
        drop_whitespace: bool = ...,
        max_lines: int = ...,
        placeholder: str = ...
) -> List[str]:
    ...

def fill(
        text: str,
        width: int = ...,
        *,
        initial_indent: str = ...,
        subsequent_indent: str = ...,
        expand_tabs: bool = ...,
        tabsize: int = ...,
        replace_whitespace: bool = ...,
        fix_sentence_endings: bool = ...,
        break_long_words: bool = ...,
        break_on_hyphens: bool = ...,
        drop_whitespace: bool = ...,
        max_lines: int = ...,
        placeholder: str = ...
):
    ...

def shorten(
        text: str,
        width: int,
        *,
        initial_indent: str = ...,
        subsequent_indent: str = ...,
        expand_tabs: bool = ...,
        tabsize: int = ...,
        replace_whitespace: bool = ...,
        fix_sentence_endings: bool = ...,
        break_long_words: bool = ...,
        break_on_hyphens: bool = ...,
        drop_whitespace: bool = ...,
        # Omit `max_lines: int = None`, it is forced to 1 here.
        placeholder: str = ...
):
    ...

def dedent(text: str) -> str:
    ...

def indent(text: str, prefix: str, predicate: Callable[[str], bool] = ...) -> str:
    ...
