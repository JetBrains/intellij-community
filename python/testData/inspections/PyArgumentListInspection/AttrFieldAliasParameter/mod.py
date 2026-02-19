import attrs


@attrs.define()
class Person:
    name: str = attrs.field(alias="full_name")
    age: int
