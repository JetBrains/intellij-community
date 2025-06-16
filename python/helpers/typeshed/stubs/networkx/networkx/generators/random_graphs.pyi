from networkx.utils.backends import _dispatchable

__all__ = [
    "fast_gnp_random_graph",
    "gnp_random_graph",
    "dense_gnm_random_graph",
    "gnm_random_graph",
    "erdos_renyi_graph",
    "binomial_graph",
    "newman_watts_strogatz_graph",
    "watts_strogatz_graph",
    "connected_watts_strogatz_graph",
    "random_regular_graph",
    "barabasi_albert_graph",
    "dual_barabasi_albert_graph",
    "extended_barabasi_albert_graph",
    "powerlaw_cluster_graph",
    "random_lobster",
    "random_shell_graph",
    "random_powerlaw_tree",
    "random_powerlaw_tree_sequence",
    "random_kernel_graph",
]

@_dispatchable
def fast_gnp_random_graph(n, p, seed=None, directed: bool = False): ...
@_dispatchable
def gnp_random_graph(n, p, seed=None, directed: bool = False): ...

binomial_graph = gnp_random_graph
erdos_renyi_graph = gnp_random_graph

@_dispatchable
def dense_gnm_random_graph(n, m, seed=None): ...
@_dispatchable
def gnm_random_graph(n, m, seed=None, directed: bool = False): ...
@_dispatchable
def newman_watts_strogatz_graph(n, k, p, seed=None): ...
@_dispatchable
def watts_strogatz_graph(n, k, p, seed=None): ...
@_dispatchable
def connected_watts_strogatz_graph(n, k, p, tries: int = 100, seed=None): ...
@_dispatchable
def random_regular_graph(d, n, seed=None): ...
@_dispatchable
def barabasi_albert_graph(n, m, seed=None, initial_graph=None): ...
@_dispatchable
def dual_barabasi_albert_graph(n, m1, m2, p, seed=None, initial_graph=None): ...
@_dispatchable
def extended_barabasi_albert_graph(n, m, p, q, seed=None): ...
@_dispatchable
def powerlaw_cluster_graph(n, m, p, seed=None): ...
@_dispatchable
def random_lobster(n, p1, p2, seed=None): ...
@_dispatchable
def random_shell_graph(constructor, seed=None): ...
@_dispatchable
def random_powerlaw_tree(n, gamma: float = 3, seed=None, tries: int = 100): ...
@_dispatchable
def random_powerlaw_tree_sequence(n, gamma: float = 3, seed=None, tries: int = 100): ...
@_dispatchable
def random_kernel_graph(n, kernel_integral, kernel_root=None, seed=None): ...
