from pydantic import Field, ConfigDict
from pydantic.dataclasses import dataclass

@dataclass(config=ConfigDict(populate_by_name=False))
class Model:
    __pydantic_config__ = ConfigDict(populate_by_name=True)

    a1: str = Field(alias="a2", frozen=True)

Model(<arg1>)