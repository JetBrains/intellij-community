from pydantic import Field
from grand import Grand

class Base(Grand, validate_by_name=True, validate_by_alias=False):
    base_field: str = Field(alias="base_alias")