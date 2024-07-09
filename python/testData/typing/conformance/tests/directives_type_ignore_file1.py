#!/usr/bin/env python

# type: ignore

"""
Tests a file-level type ignore comment.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#type-ignore-comments

# > A # type: ignore comment on a line by itself at the top of a file, before any
# > docstrings, imports, or other executable code, silences all errors in the file.
# > Blank lines and other comments, such as shebang lines and coding cookies, may
# > precede the # type: ignore comment.

x: int = ""  # No error should be reported
