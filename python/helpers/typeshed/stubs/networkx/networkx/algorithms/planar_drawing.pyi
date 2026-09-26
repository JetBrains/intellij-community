from _typeshed import Incomplete
from collections.abc import Sequence

from networkx.algorithms.planarity import PlanarEmbedding
from networkx.utils.backends import _dispatchable

__all__ = ["combinatorial_embedding_to_pos"]

@_dispatchable
def combinatorial_embedding_to_pos(
    embedding: PlanarEmbedding[Incomplete], fully_triangulate: bool = False
) -> dict[Incomplete, Incomplete]: ...
def set_position(parent, tree, remaining_nodes, delta_x, y_coordinate, pos): ...
def get_canonical_ordering(embedding: PlanarEmbedding[Incomplete], outer_face: Sequence[Incomplete]) -> list[Incomplete]: ...
def triangulate_face(embedding: PlanarEmbedding[Incomplete], v1, v2): ...
def triangulate_embedding(
    embedding: PlanarEmbedding[Incomplete], fully_triangulate: bool = True
) -> tuple[PlanarEmbedding[Incomplete], list[Incomplete]]: ...
def make_bi_connected(
    embedding: PlanarEmbedding[Incomplete], starting_node, outgoing_node, edges_counted: set[tuple[Incomplete, Incomplete]]
) -> list[Incomplete]: ...
