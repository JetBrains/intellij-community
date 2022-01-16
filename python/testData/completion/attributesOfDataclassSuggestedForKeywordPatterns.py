import dataclasses


@dataclasses.dataclass
class C:
    count: int
    name: str


match C(count=42, name='foo'):
    case C(<caret>):
        pass
