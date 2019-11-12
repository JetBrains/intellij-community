from pydantic import dataclasses

@dataclasses.dataclass
class A1:
    x: int = 0

    def __post_init_post_parse__(self):
        pass

    def __post_init__(self):