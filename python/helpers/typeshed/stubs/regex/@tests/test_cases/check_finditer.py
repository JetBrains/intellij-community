from __future__ import annotations

from typing import List
from typing_extensions import assert_type

import regex

# Regression tests for #9263
assert_type(list(regex.finditer(r"foo", "foo")), List[regex.Match[str]])
pat = regex.compile(rb"foo")
assert_type(list(pat.finditer(b"foo")), List[regex.Match[bytes]])
