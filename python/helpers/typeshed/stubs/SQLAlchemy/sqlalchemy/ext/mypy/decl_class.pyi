from typing import Any
from typing_extensions import TypeAlias

from . import util

_SemanticAnalyzerPluginInterface: TypeAlias = Any  # mypy.plugin.SemanticAnalyzerPluginInterface

def scan_declarative_assignments_and_apply_types(
    cls, api: _SemanticAnalyzerPluginInterface, is_mixin_scan: bool = ...
) -> list[util.SQLAlchemyAttribute] | None: ...
