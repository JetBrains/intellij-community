from pydantic import BaseModel, Field
from pydantic.aliases import AliasChoices

class Model(BaseModel):
    my_field: str = Field(validation_alias=AliasChoices('alias1', 'alias2'))

Model(<arg1>)