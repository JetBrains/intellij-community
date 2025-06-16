#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from pipes import Template

table: dict[str, Template]
t: Template
uncompress: Template

class error(Exception): ...

def toaiff(filename: str) -> str: ...
def _toaiff(filename: str, temps: list[str]) -> str: ...
