# pyright: reportWildcardImportFromLibrary=false
"""
This tests that star imports work when using "all += " syntax.
"""
from __future__ import annotations

from typing import *
from zipfile import *

x: Annotated[int, 42]

p: Path
