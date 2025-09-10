from __future__ import annotations

import mmap
import re
from typing import AnyStr, Match, Optional
from typing_extensions import assert_type


def check_search(str_pat: re.Pattern[str], bytes_pat: re.Pattern[bytes]) -> None:
    assert_type(str_pat.search("x"), Optional[Match[str]])
    assert_type(bytes_pat.search(b"x"), Optional[Match[bytes]])
    assert_type(bytes_pat.search(bytearray(b"x")), Optional[Match[bytes]])
    assert_type(bytes_pat.search(mmap.mmap(0, 10)), Optional[Match[bytes]])


def check_search_with_AnyStr(pattern: re.Pattern[AnyStr], string: AnyStr) -> re.Match[AnyStr]:
    """See issue #9591"""
    match = pattern.search(string)
    if match is None:
        raise ValueError(f"'{string!r}' does not match {pattern!r}")
    return match


def check_no_ReadableBuffer_false_negatives() -> None:
    re.compile("foo").search(bytearray(b"foo"))  # type: ignore
    re.compile("foo").search(mmap.mmap(0, 10))  # type: ignore
