from pydantic import BaseModel, Field

class Model(BaseModel, validate_by_name=True, validate_by_alias=True):
    my_field: str = Field(alias='my_alias')

Model(<arg1>)