#  Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

from typing import Any
from typing_extensions import TypeAlias

from . import util

_SemanticAnalyzerPluginInterface: TypeAlias = Any  # mypy.plugin.SemanticAnalyzerPluginInterface

def scan_declarative_assignments_and_apply_types(
    cls, api: _SemanticAnalyzerPluginInterface, is_mixin_scan: bool = ...
) -> list[util.SQLAlchemyAttribute] | None: ...
