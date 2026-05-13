from pydantic import BaseModel, Field

class Grand(BaseModel, validate_by_name=False, validate_by_alias=True):
    grand_field: str = Field(alias="grand_alias")