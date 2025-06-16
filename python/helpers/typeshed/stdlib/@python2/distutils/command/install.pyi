#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from distutils.cmd import Command
from typing import Text

class install(Command):
    user: bool
    prefix: Text | None
    home: Text | None
    root: Text | None
    install_lib: Text | None
    def initialize_options(self) -> None: ...
    def finalize_options(self) -> None: ...
    def run(self) -> None: ...
