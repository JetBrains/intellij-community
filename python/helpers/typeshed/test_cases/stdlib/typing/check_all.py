# pyright: reportWildcardImportFromLibrary=false

"""
This tests that star imports work when using "all += " syntax.
"""

import sys
from typing import *  # noqa: F403
from zipfile import *  # noqa: F403

if sys.version_info >= (3, 9):
    x: Annotated[int, 42]  # noqa: F405

if sys.version_info >= (3, 8):
    p: Path  # noqa: F405
