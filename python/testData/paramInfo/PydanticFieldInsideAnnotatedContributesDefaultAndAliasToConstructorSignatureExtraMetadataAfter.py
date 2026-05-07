from typing import Annotated
from pydantic import BaseModel, Field, WithJsonSchema

class MyModel(BaseModel):
    a: str | None = Field(default=None, alias="A")
    b: Annotated[str | None, Field(default=None, alias="B"), WithJsonSchema({"x": "y"})]

MyModel(<arg1>)