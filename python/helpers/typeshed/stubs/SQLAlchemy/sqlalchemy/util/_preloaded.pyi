#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any

class _ModuleRegistry:
    module_registry: Any
    prefix: Any
    def __init__(self, prefix: str = ...) -> None: ...
    def preload_module(self, *deps): ...
    def import_prefix(self, path) -> None: ...

preloaded: Any
preload_module: Any
