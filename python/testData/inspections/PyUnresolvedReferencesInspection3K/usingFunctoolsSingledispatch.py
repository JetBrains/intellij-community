from functools import singledispatch


@singledispatch
def to_description(ob):
    return str(ob)


@to_description.register(type(None))
def none_to_description(_):
    return '–'


@to_description.register(bool)
def bool_to_description(b):
    return '✓' if b else ''
