import dataclasses


@dataclasses.dataclass
class Base:
    base_field_no_default: int = dataclasses.MISSING
    base_field_default: int = 42
