from _typeshed import Incomplete

from networkx.utils.backends import _dispatchable

from ..classes.digraph import DiGraph
from ..classes.graph import Graph
from ..classes.multidigraph import MultiDiGraph
from ..classes.multigraph import MultiGraph

__all__ = [
    "configuration_model",
    "directed_configuration_model",
    "expected_degree_graph",
    "havel_hakimi_graph",
    "directed_havel_hakimi_graph",
    "degree_sequence_tree",
    "random_degree_sequence_graph",
]

@_dispatchable
def configuration_model(
    deg_sequence: list[int], create_using: MultiGraph[Incomplete] | type[MultiGraph[Incomplete]] | None = None, seed=None
) -> MultiGraph[Incomplete]: ...
@_dispatchable
def directed_configuration_model(
    in_degree_sequence: list[int],
    out_degree_sequence: list[int],
    create_using: MultiDiGraph[Incomplete] | type[MultiDiGraph[Incomplete]] | None = None,
    seed=None,
) -> MultiDiGraph[Incomplete]: ...
@_dispatchable
def expected_degree_graph(w: list[Incomplete], seed=None, selfloops: bool = True) -> Graph[Incomplete]: ...
@_dispatchable
def havel_hakimi_graph(deg_sequence: list[int], create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None): ...
@_dispatchable
def directed_havel_hakimi_graph(
    in_deg_sequence: list[int],
    out_deg_sequence: list[int],
    create_using: DiGraph[Incomplete] | type[DiGraph[Incomplete]] | None = None,
) -> DiGraph[Incomplete]: ...
@_dispatchable
def degree_sequence_tree(
    deg_sequence, create_using: Graph[Incomplete] | type[Graph[Incomplete]] | None = None
) -> Graph[Incomplete]: ...
@_dispatchable
def random_degree_sequence_graph(sequence: list[int], seed=None, tries: int = 10) -> Graph[Incomplete]: ...

class DegreeSequenceRandomGraph:
    rng: Incomplete
    degree: Incomplete
    m: Incomplete
    dmax: Incomplete
    def __init__(self, degree, rng) -> None: ...
    remaining_degree: Incomplete
    graph: Incomplete
    def generate(self): ...
    def update_remaining(self, u, v, aux_graph=None) -> None: ...
    def p(self, u, v): ...
    def q(self, u, v): ...
    def suitable_edge(self): ...
    def phase1(self) -> None: ...
    def phase2(self) -> None: ...
    def phase3(self) -> None: ...
