from typing_extensions import assert_type

import networkx as nx
from networkx.classes.reportviews import DegreeView, DiDegreeView

# Test covariant dict type for `pos` in nx_latex functions
G: "nx.Graph[int]" = nx.Graph([(1, 2), (2, 3), (3, 4)])
nx.to_latex_raw(G, pos=nx.spring_layout(G, seed=42))  # OK: dict[node, ndarray]
pos1: dict[int, tuple[int, int]] = {1: (1, 2), 2: (3, 4), 3: (5, 6), 4: (7, 8)}
nx.to_latex_raw(G, pos=pos1)  # OK: dict[node, 2-tuple]
pos2: dict[int, str] = {1: "(1, 2)", 2: "(3, 4)", 3: "(5, 6)", 4: "(7, 8)"}
nx.to_latex_raw(G, pos=pos2)  # OK: dict[node, str]
pos3: dict[int, int] = {1: 1, 2: 3, 3: 5, 4: 7}
nx.to_latex_raw(G, pos=pos3)  # type: ignore # dict keys must be str or collection

# Test that we don't confuse str and Iterable[str] in DiDegreeView.__call__
G_str = nx.Graph[str]()
di_degree_view = DiDegreeView(G_str)
assert_type(di_degree_view(""), int)
assert_type(di_degree_view([""]), DiDegreeView[str])
assert_type(di_degree_view({""}), DiDegreeView[str])
degree_view = DegreeView(G_str)
assert_type(degree_view(""), int)
assert_type(degree_view([""]), DegreeView[str])
assert_type(degree_view({""}), DegreeView[str])
