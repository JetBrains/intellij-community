from networkx.utils.backends import _dispatchable

__all__ = ["tree_data", "tree_graph"]

def tree_data(G, root, ident: str = "id", children: str = "children"): ...
@_dispatchable
def tree_graph(data, ident: str = "id", children: str = "children"): ...
