def i_have_special_attributes():
    pass


def print_a_callable_name(callable):
    print(callable.__name__)


def print_a_callable_doc(callable):
    print(callable.__doc__)


def print_a_callable_unknown(callable):
    print(callable.unknown)


if __name__ == "__main__":
    print_a_callable_name(i_have_special_attributes)
    print_a_callable_doc(i_have_special_attributes)
    print_a_callable_unknown(<warning descr="Expected type '{unknown}', got '() -> None' instead">i_have_special_attributes</warning>)
