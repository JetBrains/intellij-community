import dataclasses

@dataclasses.dataclass
class A:
    a: int
    b: dataclasses.InitVar[str]
    c: dataclasses.InitVar[bytes]

    def __post_init__<error descr="'__post_init__' should take all init-only variables in the same order as they are defined">(self)</error>:
        pass

@dataclasses.dataclass
class B:
    a: int
    b: dataclasses.InitVar[str]
    c: dataclasses.InitVar[bytes]

    def __post_init__<warning descr="'__post_init__' should take all init-only variables in the same order as they are defined">(self, c, b)</warning>:
        pass