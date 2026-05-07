from pydantic import Field
from pydantic.dataclasses import dataclass

from config import CONFIG

@dataclass(config=CONFIG)
class Model:
    a1: str = Field(alias="a2", frozen=True)