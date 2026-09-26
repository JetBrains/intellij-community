"""
Tests checking the version or platform.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#version-and-platform-checking

import os
import sys
from typing import assert_type

def test(a: int, b: str):
    val1: int | str
    if sys.version_info >= (3, 8):
        val1 = a
    else:
        val1 = b

    assert_type(val1, int)

    val2: int | str
    if sys.version_info >= (3, 8, 0):
        val2 = a
    else:
        val2 = b

    assert_type(val2, int)  # E?: May not generate an error (support for three-element sys.version is optional)

    if sys.version_info < (3, 8):
        val3 = ""
    else:
        val4 = ""

    this_raises = val3  # E: `val3` is undefined
    does_not_raise = val4  # should not error

    val5: int | str
    if sys.version_info < (3, 100, 0):
        val5 = a
    else:
        val5 = b

    assert_type(val5, int)  # E?: May not generate an error (support for three-element sys.version is optional)


    if sys.platform == "bogus_platform":
        val6 = ""
    else:
        val7 = ""

    this_raises = val6  # E: `val6` is undefined
    does_not_raise = val7  # should not error

    if sys.platform != "bogus_platform":
        val8 = ""
    else:
        val9 = ""

    does_not_raise = val8  # should not error
    this_raises = val9  # E: `val9` is undefined

    if os.name == "bogus_os":
        val10 = ""
    else:
        val11 = ""

    this_raises = val10  # E?: `val10` is undefined, but support for `os.name` is optional
    does_not_raise = val11  # E? should not error if `os.name` control flow is supported, but might be flagged as possibly undefined otherwise

    if os.name != "bogus_os":
        val12 = ""
    else:
        val13 = ""

    does_not_raise = val12  # E?: should not error if `os.name` control flow is supported, but might be flagged as possibly undefined otherwise
    this_raises = val13  # E?: `val13` is undefined, but support for `os.name` is optional
