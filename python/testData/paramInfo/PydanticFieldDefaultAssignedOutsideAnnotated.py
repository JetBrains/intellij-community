from typing import Annotated
from pydantic import BaseModel, Field

class MyModel(BaseModel):
    b: Annotated[int, Field(alias="B")] = 0

MyModel(<arg1>)
