from networkx.utils.backends import _dispatchable

__all__ = ["attr_matrix", "attr_sparse_matrix"]

@_dispatchable
def attr_matrix(G, edge_attr=None, node_attr=None, normalized: bool = False, rc_order=None, dtype=None, order=None): ...
@_dispatchable
def attr_sparse_matrix(G, edge_attr=None, node_attr=None, normalized: bool = False, rc_order=None, dtype=None): ...
