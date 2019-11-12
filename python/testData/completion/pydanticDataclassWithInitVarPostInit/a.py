import dataclasses
import pydantic

@pydantic.dataclasses.dataclass
class A1:
    x: int = 0
    y: dataclasses.InitVar[int] = 1

    def __post_init_post_parse__(self, y: int):
        pass

    def __post<caret>