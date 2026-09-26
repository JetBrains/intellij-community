"""Logic for creating models."""
from typing import ClassVar

from ._internal import (
    _model_construction,
)
from .config import ConfigDict

__all__ = 'BaseModel'

class BaseModel(metaclass=_model_construction.ModelMetaclass):
    model_config: ClassVar[ConfigDict]
