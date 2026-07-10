from pydantic import BaseModel, Field

class M(BaseModel):
    a: str = Field(validation_alias=a)

M(<arg1>)