from __future__ import annotations

from typing import Any

from pkg_resources import (
    DefaultProvider,
    EggMetadata,
    EggProvider,
    EmptyProvider,
    FileMetadata,
    IMetadataProvider,
    IResourceProvider,
    NullProvider,
    PathMetadata,
    ZipProvider,
)
from setuptools.command.editable_wheel import EditableStrategy, _LinkTree, _StaticPth, _TopLevelFinder
from setuptools.config.expand import EnsurePackagesDiscovered
from setuptools.config.pyprojecttoml import _EnsurePackagesDiscovered

# We don't care about the __init__ methods, only about if an instance respects the Protocol
_: Any = object()

# Test IMetadataProvider Protocol implementers
metadata_provider: IMetadataProvider
metadata_provider = NullProvider(_)
metadata_provider = EggProvider(_)
metadata_provider = EmptyProvider()
metadata_provider = DefaultProvider(_)
metadata_provider = ZipProvider(_)
metadata_provider = FileMetadata(_)
metadata_provider = PathMetadata(_, _)
metadata_provider = EggMetadata(_)

# Test IResourceProvider Protocol implementers
resource_provider: IResourceProvider
resource_provider = NullProvider(_)
resource_provider = EggProvider(_)
resource_provider = EmptyProvider()
resource_provider = DefaultProvider(_)
resource_provider = ZipProvider(_)
resource_provider = FileMetadata(_)
resource_provider = PathMetadata(_, _)
resource_provider = EggMetadata(_)


# Test EditableStrategy Protocol implementers
editable_strategy: EditableStrategy
editable_strategy = _StaticPth(_, _, _)
editable_strategy = _LinkTree(_, _, _, _)
editable_strategy = _TopLevelFinder(_, _)
# Not EditableStrategy due to incompatible __call__ method
editable_strategy = EnsurePackagesDiscovered(_)  # type: ignore
editable_strategy = _EnsurePackagesDiscovered(_, _, _)  # type: ignore
