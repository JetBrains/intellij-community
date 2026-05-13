from pydantic import BaseModel, Field

class Base(BaseModel, validate_by_name=True, validate_by_alias=False):
    inherited_field: str = Field(alias="inherited_alias")