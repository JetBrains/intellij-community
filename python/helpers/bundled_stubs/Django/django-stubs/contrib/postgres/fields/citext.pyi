from django.db.models.fields import CharField, EmailField, TextField
from typing_extensions import TypeVar

# __set__ value type
_ST = TypeVar("_ST", contravariant=True)
# __get__ return type
_GT = TypeVar("_GT", covariant=True)

class CICharField(CharField[_ST, _GT]): ...
class CIEmailField(EmailField[_ST, _GT]): ...
class CITextField(TextField[_ST, _GT]): ...

__all__ = ["CICharField", "CIEmailField", "CITextField"]
