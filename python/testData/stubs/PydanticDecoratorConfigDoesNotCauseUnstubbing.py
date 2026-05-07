from pydantic import Field, ConfigDict
from pydantic.dataclasses import dataclass

@dataclass(config=ConfigDict(populate_by_name=True))
class Model:
    a1: str = Field(alias="a2")