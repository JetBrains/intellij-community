import typing


def too_few_arguments__correct_types(a: int, b: str) -> bool:
    return True

def too_few_arguments__wrong_types(a: int, b: int) -> bool:
    return True

def matching_number_arguments__correct_types(a: int, b: str, c: int) -> bool:
    return True

def matching_number_arguments__wrong_types(a: int, b: str, c: str) -> bool:
    return True

def too_many_arguments__correct_types(a: int, b: str, c: int, d: str) -> bool:
    return True

def too_many_arguments__wrong_types(a: int, b: str, c: str, d: str) -> bool:
    return True


def foo(callback: typing.Callable[[int, str, int], bool]) -> None:
    callback(1, 'abc', 2)


foo(<warning descr="Expected type '(int, str, int) -> bool', got '(a: int, b: str) -> bool' instead">too_few_arguments__correct_types</warning>)
foo(<warning descr="Expected type '(int, str, int) -> bool', got '(a: int, b: int) -> bool' instead">too_few_arguments__wrong_types</warning>)
foo(matching_number_arguments__correct_types)
foo(<warning descr="Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: str) -> bool' instead">matching_number_arguments__wrong_types</warning>)
foo(<warning descr="Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: int, d: str) -> bool' instead">too_many_arguments__correct_types</warning>)
foo(<warning descr="Expected type '(int, str, int) -> bool', got '(a: int, b: str, c: str, d: str) -> bool' instead">too_many_arguments__wrong_types</warning>)