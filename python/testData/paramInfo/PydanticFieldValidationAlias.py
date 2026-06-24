from pydantic import BaseModel, Field

class Model(BaseModel):
    my_field: str = Field(validation_alias='my_alias')

Model(<arg1>)