from typing import Type


class MyClass(object):
    pass


def func(param):
    # type: (Type[MyClass]) -> MyClass
    param()
