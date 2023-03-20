from typing import Any
from typing_extensions import TypeAlias

from . import util

_AssignmentStmt: TypeAlias = Any  # mypy.nodes.AssignmentStmt
_NameExpr: TypeAlias = Any  # mypy.nodes.NameExpr
_StrExpr: TypeAlias = Any  # mypy.nodes.StrExpr
_SemanticAnalyzerPluginInterface: TypeAlias = Any  # mypy.plugin.SemanticAnalyzerPluginInterface
_ProperType: TypeAlias = Any  # mypy.types.ProperType

def apply_mypy_mapped_attr(
    cls, api: _SemanticAnalyzerPluginInterface, item: _NameExpr | _StrExpr, attributes: list[util.SQLAlchemyAttribute]
) -> None: ...
def re_apply_declarative_assignments(
    cls, api: _SemanticAnalyzerPluginInterface, attributes: list[util.SQLAlchemyAttribute]
) -> None: ...
def apply_type_to_mapped_statement(
    api: _SemanticAnalyzerPluginInterface,
    stmt: _AssignmentStmt,
    lvalue: _NameExpr,
    left_hand_explicit_type: _ProperType | None,
    python_type_for_type: _ProperType | None,
) -> None: ...
def add_additional_orm_attributes(
    cls, api: _SemanticAnalyzerPluginInterface, attributes: list[util.SQLAlchemyAttribute]
) -> None: ...
