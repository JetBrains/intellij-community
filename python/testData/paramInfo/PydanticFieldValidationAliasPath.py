from pydantic import BaseModel, Field, AliasPath

class Model(BaseModel):
    my_field: str = Field(validation_alias=AliasPath('my_alias_path', 0))

Model(<arg1>)