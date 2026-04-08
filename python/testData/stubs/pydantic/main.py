"""Logic for creating models."""

from ._internal import (
    _model_construction,
)

__all__ = 'BaseModel'

class BaseModel(metaclass=_model_construction.ModelMetaclass):
    ...