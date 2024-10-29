"""
Tests checking the version or platform.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/directives.html#version-and-platform-checking

import os
import sys


if sys.version_info >= (3, 8):
    pass
else:
    val1: int = ""  # Should not generate an error

if sys.version_info >= (3, 8, 0):
    pass
else:
    val2: int = ""  # E?: May not generate an error (support for three-element sys.version is optional)

if sys.version_info < (3, 8):
    val3: int = ""  # Should not generate an error

if sys.version_info < (3, 100, 0):
    pass
else:
    val3: int = ""  # E?: May not generate an error (support for three-element sys.version is optional)


if sys.platform == "bogus_platform":
    val5: int = ""  # Should not generate an error

if sys.platform != "bogus_platform":
    pass
else:
    val6: int = ""  # Should not generate an error


if os.name == "bogus_os":
    val7: int = ""  # E?: May not generate an error (support for os.name is optional)

if os.name != "bogus_platform":
    pass
else:
    val8: int = ""  # E?: May not generate an error (support for os.name is optional)
