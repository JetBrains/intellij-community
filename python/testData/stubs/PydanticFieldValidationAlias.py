from pydantic import BaseModel, Field, AliasChoices, AliasPath

class Model(BaseModel):
    a: str = Field(validation_alias=AliasChoices("aa"))
    b: str = Field(validation_alias=AliasChoices("ba", AliasPath("names", 0)))