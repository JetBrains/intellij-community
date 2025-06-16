from _typeshed import Incomplete
from collections.abc import Collection, Iterable, Sequence

from networkx.classes.graph import Graph, _Node

__all__ = [
    "display",
    "apply_matplotlib_colors",
    "draw",
    "draw_networkx",
    "draw_networkx_nodes",
    "draw_networkx_edges",
    "draw_networkx_labels",
    "draw_networkx_edge_labels",
    "draw_bipartite",
    "draw_circular",
    "draw_kamada_kawai",
    "draw_random",
    "draw_spectral",
    "draw_spring",
    "draw_planar",
    "draw_shell",
    "draw_forceatlas2",
]

def apply_matplotlib_colors(
    G: Graph[_Node], src_attr: str, dest_attr: str, map, vmin: float | None = None, vmax: float | None = None, nodes: bool = True
) -> None: ...
def display(
    G: Graph[_Node],
    canvas=None,
    *,
    pos=...,
    node_visible: str | bool = ...,
    node_color: str = ...,
    node_size: str | float = ...,
    node_label: str | bool = ...,
    node_shape: str = ...,
    node_alpha: str = ...,
    node_border_width: str = ...,
    node_border_color: str = ...,
    edge_visible: str | bool = ...,
    edge_width: str | int = ...,
    edge_color=...,
    edge_label: str = ...,
    edge_style: str = ...,
    edge_alpha: str | float = ...,
    arrowstyle: str = ...,
    arrowsize: str | int = ...,
    edge_curvature: str = ...,
    edge_source_margin: str | int = ...,
    edge_target_margin: str | int = ...,
    hide_ticks: bool = True,
): ...
def draw(G: Graph[_Node], pos=None, ax=None, **kwds) -> None: ...
def draw_networkx(G: Graph[_Node], pos=None, arrows=None, with_labels: bool = True, **kwds) -> None: ...
def draw_networkx_nodes(
    G: Graph[_Node],
    pos,
    nodelist: Collection[Incomplete] | None = None,
    node_size: Incomplete | int = 300,
    node_color: str | Sequence[str] = "#1f78b4",
    node_shape: str = "o",
    alpha=None,
    cmap=None,
    vmin=None,
    vmax=None,
    ax=None,
    linewidths=None,
    edgecolors=None,
    label=None,
    margins=None,
    hide_ticks: bool = True,
): ...
def draw_networkx_edges(
    G: Graph[_Node],
    pos,
    edgelist=None,
    width: float = 1.0,
    edge_color: str = "k",
    style: str = "solid",
    alpha=None,
    arrowstyle=None,
    arrowsize: int = 10,
    edge_cmap=None,
    edge_vmin=None,
    edge_vmax=None,
    ax=None,
    arrows=None,
    label=None,
    node_size: Incomplete | int = 300,
    nodelist: list[Incomplete] | None = None,
    node_shape: str = "o",
    connectionstyle: str = "arc3",
    min_source_margin: int = 0,
    min_target_margin: int = 0,
    hide_ticks: bool = True,
): ...
def draw_networkx_labels(
    G: Graph[_Node],
    pos,
    labels=None,
    font_size: int = 12,
    font_color: str = "k",
    font_family: str = "sans-serif",
    font_weight: str = "normal",
    alpha=None,
    bbox=None,
    horizontalalignment: str = "center",
    verticalalignment: str = "center",
    ax=None,
    clip_on: bool = True,
    hide_ticks: bool = True,
): ...
def draw_networkx_edge_labels(
    G: Graph[_Node],
    pos,
    edge_labels=None,
    label_pos: float = 0.5,
    font_size: int = 10,
    font_color: str = "k",
    font_family: str = "sans-serif",
    font_weight: str = "normal",
    alpha=None,
    bbox=None,
    horizontalalignment: str = "center",
    verticalalignment: str = "center",
    ax=None,
    rotate: bool = True,
    clip_on: bool = True,
    node_size: int = 300,
    nodelist: list[Incomplete] | None = None,
    connectionstyle: str = "arc3",
    hide_ticks: bool = True,
): ...
def draw_bipartite(G: Graph[_Node], **kwargs): ...
def draw_circular(G: Graph[_Node], **kwargs) -> None: ...
def draw_kamada_kawai(G: Graph[_Node], **kwargs) -> None: ...
def draw_random(G: Graph[_Node], **kwargs) -> None: ...
def draw_spectral(G: Graph[_Node], **kwargs) -> None: ...
def draw_spring(G: Graph[_Node], **kwargs) -> None: ...
def draw_shell(G: Graph[_Node], nlist=None, **kwargs) -> None: ...
def draw_planar(G: Graph[_Node], **kwargs) -> None: ...
def draw_forceatlas2(G: Graph[_Node], **kwargs) -> None: ...
def apply_alpha(
    colors, alpha: float | Iterable[float], elem_list, cmap=None, vmin: float | None = None, vmax: float | None = None
): ...
