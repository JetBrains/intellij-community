from typing_extensions import Sentinel, assert_type

# > Sentinel objects may be used in type annotations if they are defined using
# > a simple assignment of the form ``NAME = sentinel('NAME')`` in the
# > global scope or in a class body that is not within a function.

MISSING = Sentinel("<MISSING>")  # name is not required to match the variable name
SPECIAL = Sentinel("SPECIAL")

class Cls:
    IN_CLASS = Sentinel("Cls.IN_CLASS")


def func1(x: int = MISSING) -> None:  # E: incompatible default
    pass

# > Type checkers must support narrowing union types involving sentinels using the
# > ``is`` and ``is not`` operators

def func2(x: int | MISSING | SPECIAL = MISSING) -> None:
    if x is MISSING:
        assert_type(x, MISSING)
    else:
        assert_type(x, int | SPECIAL)

def func3(x: int | Cls.IN_CLASS = Cls.IN_CLASS) -> None:
    if x is Cls.IN_CLASS:
        assert_type(x, Cls.IN_CLASS)
    else:
        assert_type(x, int)


func2(1)  # ok
func2(MISSING)  # ok
func2(SPECIAL)  # ok
func2(Cls.IN_CLASS)  # E: incompatible argument

func3(1)  # ok
func3(MISSING)  # E: incompatible argument
func3(Cls.IN_CLASS)  # ok
