from _typeshed import Incomplete

from antlr4.atn.ATNConfigSet import ATNConfigSet as ATNConfigSet
from antlr4.atn.SemanticContext import SemanticContext as SemanticContext

class PredPrediction:
    alt: Incomplete
    pred: Incomplete
    def __init__(self, pred: SemanticContext, alt: int) -> None: ...

class DFAState:
    stateNumber: Incomplete
    configs: Incomplete
    edges: Incomplete
    isAcceptState: bool
    prediction: int
    lexerActionExecutor: Incomplete
    requiresFullContext: bool
    predicates: Incomplete
    def __init__(self, stateNumber: int = -1, configs: ATNConfigSet = ...) -> None: ...
    def getAltSet(self): ...
    def __hash__(self): ...
    def __eq__(self, other): ...
