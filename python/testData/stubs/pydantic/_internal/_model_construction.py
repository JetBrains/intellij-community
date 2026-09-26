"""Private logic for creating models."""

from abc import ABCMeta
from typing import TYPE_CHECKING

from typing import dataclass_transform

if TYPE_CHECKING:
    from ..fields import Field as PydanticModelField
    from ..fields import PrivateAttr as PydanticModelPrivateAttr
else:
    PydanticModelField = object()
    PydanticModelPrivateAttr = object()


class NoInitField:
    ...


@dataclass_transform(kw_only_default=True, field_specifiers=(PydanticModelField, PydanticModelPrivateAttr, NoInitField))
class ModelMetaclass(ABCMeta):
    ...