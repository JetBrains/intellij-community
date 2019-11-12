import dataclasses
import pydantic

@pydantic.dataclasses.dataclass
class A:
    a: int
    b: dataclasses.InitVar[str]
    c: dataclasses.InitVar[bytes]

    def __post_init__(self, b: str, c: bytes):
        pass

    def __post_init_post_parse__<error descr="'__post_init_post_parse__' should take all init-only variables in the same order as they are defined">(self)</error>:
        pass

@pydantic.dataclasses.dataclass
class B:
    a: int
    b: dataclasses.InitVar[str]
    c: dataclasses.InitVar[bytes]

    def __post_init__(self, b: str, c: bytes):
        pass

    def __post_init_post_parse__<warning descr="'__post_init_post_parse__' should take all init-only variables in the same order as they are defined">(self, c, b)</warning>:
        pass