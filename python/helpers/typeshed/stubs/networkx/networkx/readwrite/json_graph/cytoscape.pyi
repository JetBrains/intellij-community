from networkx.utils.backends import _dispatchable

__all__ = ["cytoscape_data", "cytoscape_graph"]

def cytoscape_data(G, name: str = "name", ident: str = "id"): ...
@_dispatchable
def cytoscape_graph(data, name: str = "name", ident: str = "id"): ...
