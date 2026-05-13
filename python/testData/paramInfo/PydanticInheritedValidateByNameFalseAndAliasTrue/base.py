from pydantic import BaseModel, Field

class Base(BaseModel, validate_by_name=False, validate_by_alias=True):
    inherited_field: str = Field(alias="inherited_alias")