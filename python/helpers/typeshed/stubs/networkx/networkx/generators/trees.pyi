from _typeshed import Incomplete

from networkx.utils.backends import _dispatchable

__all__ = [
    "prefix_tree",
    "prefix_tree_recursive",
    "random_labeled_tree",
    "random_labeled_rooted_tree",
    "random_labeled_rooted_forest",
    "random_unlabeled_tree",
    "random_unlabeled_rooted_tree",
    "random_unlabeled_rooted_forest",
]

@_dispatchable
def prefix_tree(paths): ...
@_dispatchable
def prefix_tree_recursive(paths): ...
@_dispatchable
def random_labeled_tree(n, *, seed=None): ...
@_dispatchable
def random_labeled_rooted_tree(n, *, seed=None): ...
@_dispatchable
def random_unlabeled_rooted_tree(n, *, number_of_trees=None, seed=None) -> Incomplete | list[Incomplete]: ...
@_dispatchable
def random_labeled_rooted_forest(n, *, seed=None): ...
@_dispatchable
def random_unlabeled_rooted_forest(n, *, q=None, number_of_forests=None, seed=None) -> Incomplete | list[Incomplete]: ...
@_dispatchable
def random_unlabeled_tree(n, *, number_of_trees=None, seed=None) -> Incomplete | list[Incomplete]: ...
