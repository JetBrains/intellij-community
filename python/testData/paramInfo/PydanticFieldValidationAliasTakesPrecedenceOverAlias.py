from pydantic import BaseModel, Field

class Model(BaseModel):
    my_field: str = Field(alias='my_alias', validation_alias='my_validation_alias')

Model(<arg1>)