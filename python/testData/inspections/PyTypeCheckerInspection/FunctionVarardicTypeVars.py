import typing


def verify_is_int(x: int):
    return "{} is an int".format(x)


def verify_is_str(x: str):
    return "{} is a str".format(x)

def verify_is_tuple(x: typing.Tuple):
    return "{} is a tuple".format(x)

T = typing.TypeVar('T', int, str)
V = typing.TypeVar('V', int, str)
def f(*args: T, **kwargs: V) -> typing.Tuple[T, V]:
    first_arg = args[0]
    print(args[<warning descr="Expected type 'Union[Integral, slice]', got 'str' instead">'test'</warning>])
    verify_is_tuple(first_arg)
    return args[0], kwargs['foo']


result = f('test', foo=2)
verify_is_int(<warning descr="Expected type 'int', got 'str' instead">result[0]</warning>)
verify_is_str(<warning descr="Expected type 'str', got 'int' instead">result[1]</warning>)
