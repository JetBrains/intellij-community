import typing


class C(typing.NamedTuple):
    count: int
    name: str


match C(count=42, name='foo'):
    case C(<caret>):
        pass
