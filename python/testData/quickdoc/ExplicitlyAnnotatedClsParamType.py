from typing import Type, TypeVar

T = TypeVar('T')


class MyClass(object):
    @classmethod
    def fac<the_ref>tory(cls):
        # type: (Type[T]) -> T
        return cls()
