from collections.abc import Sequence
from typing import Any

AssignmentStmt = Any  # from mypy.nodes
Expression = Any  # from mypy.nodes
RefExpr = Any  # from mypy.nodes
TypeInfo = Any  # from mypy.nodes
Var = Any  # from mypy.nodes
StrExpr = Any  # from mypy.nodes
SemanticAnalyzerPluginInterface = Any  # from mypy.plugin
ProperType = Any  # from mypy.types

def infer_type_from_right_hand_nameexpr(
    api: SemanticAnalyzerPluginInterface,
    stmt: AssignmentStmt,
    node: Var,
    left_hand_explicit_type: ProperType | None,
    infer_from_right_side: RefExpr,
) -> ProperType | None: ...
def infer_type_from_left_hand_type_only(
    api: SemanticAnalyzerPluginInterface, node: Var, left_hand_explicit_type: ProperType | None
) -> ProperType | None: ...
def extract_python_type_from_typeengine(
    api: SemanticAnalyzerPluginInterface, node: TypeInfo, type_args: Sequence[Expression]
) -> ProperType: ...
