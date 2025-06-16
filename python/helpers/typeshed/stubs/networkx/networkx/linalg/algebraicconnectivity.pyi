from networkx.utils.backends import _dispatchable

__all__ = ["algebraic_connectivity", "fiedler_vector", "spectral_ordering", "spectral_bisection"]

class _PCGSolver:
    def __init__(self, A, M) -> None: ...
    def solve(self, B, tol): ...

class _LUSolver:
    def __init__(self, A) -> None: ...
    def solve(self, B, tol=None): ...

@_dispatchable
def algebraic_connectivity(
    G, weight: str = "weight", normalized: bool = False, tol: float = 1e-08, method: str = "tracemin_pcg", seed=None
): ...
@_dispatchable
def fiedler_vector(
    G, weight: str = "weight", normalized: bool = False, tol: float = 1e-08, method: str = "tracemin_pcg", seed=None
): ...
@_dispatchable
def spectral_ordering(
    G, weight: str = "weight", normalized: bool = False, tol: float = 1e-08, method: str = "tracemin_pcg", seed=None
): ...
@_dispatchable
def spectral_bisection(
    G, weight: str = "weight", normalized: bool = False, tol: float = 1e-08, method: str = "tracemin_pcg", seed=None
): ...
