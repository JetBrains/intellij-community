#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from distutils.config import PyPIRCCommand
from typing import ClassVar

class upload(PyPIRCCommand):
    description: ClassVar[str]
    boolean_options: ClassVar[list[str]]
    def run(self) -> None: ...
    def upload_file(self, command, pyversion, filename) -> None: ...
