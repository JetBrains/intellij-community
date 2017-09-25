from typing import Any
from jinja2.visitor import NodeTransformer

def optimize(node, environment): ...

class Optimizer(NodeTransformer):
    environment = ...  # type: Any
    def __init__(self, environment) -> None: ...
    def visit_If(self, node): ...
    def fold(self, node): ...
    visit_Add = ...  # type: Any
    visit_Sub = ...  # type: Any
    visit_Mul = ...  # type: Any
    visit_Div = ...  # type: Any
    visit_FloorDiv = ...  # type: Any
    visit_Pow = ...  # type: Any
    visit_Mod = ...  # type: Any
    visit_And = ...  # type: Any
    visit_Or = ...  # type: Any
    visit_Pos = ...  # type: Any
    visit_Neg = ...  # type: Any
    visit_Not = ...  # type: Any
    visit_Compare = ...  # type: Any
    visit_Getitem = ...  # type: Any
    visit_Getattr = ...  # type: Any
    visit_Call = ...  # type: Any
    visit_Filter = ...  # type: Any
    visit_Test = ...  # type: Any
    visit_CondExpr = ...  # type: Any
