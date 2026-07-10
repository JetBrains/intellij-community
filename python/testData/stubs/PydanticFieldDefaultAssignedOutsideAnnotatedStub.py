from typing import Annotated
from pydantic import BaseModel, Field

class Model(BaseModel):
    b: Annotated[int, Field(alias="B")] = 0
