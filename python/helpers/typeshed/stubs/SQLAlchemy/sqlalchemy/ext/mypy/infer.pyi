from collections.abc import Sequence
from typing import Any
from typing_extensions import TypeAlias

_AssignmentStmt: TypeAlias = Any  # mypy.nodes.AssignmentStmt
_Expression: TypeAlias = Any  # mypy.nodes.Expression
_RefExpr: TypeAlias = Any  # mypy.nodes.RefExpr
_TypeInfo: TypeAlias = Any  # mypy.nodes.TypeInfo
_Var: TypeAlias = Any  # mypy.nodes.Var
_SemanticAnalyzerPluginInterface: TypeAlias = Any  # mypy.plugin.SemanticAnalyzerPluginInterface
_ProperType: TypeAlias = Any  # mypy.types.ProperType

def infer_type_from_right_hand_nameexpr(
    api: _SemanticAnalyzerPluginInterface,
    stmt: _AssignmentStmt,
    node: _Var,
    left_hand_explicit_type: _ProperType | None,
    infer_from_right_side: _RefExpr,
) -> _ProperType | None: ...
def infer_type_from_left_hand_type_only(
    api: _SemanticAnalyzerPluginInterface, node: _Var, left_hand_explicit_type: _ProperType | None
) -> _ProperType | None: ...
def extract_python_type_from_typeengine(
    api: _SemanticAnalyzerPluginInterface, node: _TypeInfo, type_args: Sequence[_Expression]
) -> _ProperType: ...
