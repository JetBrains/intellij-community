from typing import Tuple


def expects_many_ints(xs: Tuple[int, ...]):
    pass


int_and_bool = (42, True)
expects_many_ints(int_and_bool)

int_and_str = (42, 'foo')
expects_many_ints(<warning descr="Expected type 'Tuple[int, ...]', got 'Tuple[int, str]' instead">int_and_str</warning>)

booleans = (True, False)  # type: Tuple[bool, ...]
expects_many_ints(booleans)

strings = ('foo', 'bar')  # type: Tuple[str, ...]
expects_many_ints(<warning descr="Expected type 'Tuple[int, ...]', got 'Tuple[str, ...]' instead">strings</warning>)


def expects_two_ints(xs: Tuple[int, int]):
    pass


ints = (1, 2)  # type: Tuple[int, ...]
expects_two_ints(<warning descr="Expected type 'Tuple[int, int]', got 'Tuple[int, ...]' instead">ints</warning>)
