from __future__ import annotations

from typing import Any

from setuptools.command.editable_wheel import EditableStrategy, _LinkTree, _StaticPth, _TopLevelFinder
from setuptools.config.expand import EnsurePackagesDiscovered
from setuptools.config.pyprojecttoml import _EnsurePackagesDiscovered

# We don't care about the __init__ methods, only about if an instance respects the Protocol
_: Any = object()

# Test EditableStrategy Protocol implementers
editable_strategy: EditableStrategy
editable_strategy = _StaticPth(_, _, _)
editable_strategy = _LinkTree(_, _, _, _)
editable_strategy = _TopLevelFinder(_, _)
# Not EditableStrategy due to incompatible __call__ method
editable_strategy = EnsurePackagesDiscovered(_)  # type: ignore
editable_strategy = _EnsurePackagesDiscovered(_, _, _)  # type: ignore
