from functools import singledispatch

@singledispatch
def to_json(obj: object) -> object: ...
