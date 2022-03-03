from typing import Any

from . import util

SemanticAnalyzerPluginInterface = Any  # from mypy.plugin

def scan_declarative_assignments_and_apply_types(
    cls, api: SemanticAnalyzerPluginInterface, is_mixin_scan: bool = ...
) -> list[util.SQLAlchemyAttribute] | None: ...
