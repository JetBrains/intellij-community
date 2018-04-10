import six
import typing

_T = typing.TypeVar('_T')

class Meta1:
    pass

class Meta2:
    pass

class Base1:
    pass

class Base2(typing.Generic[_T]):
    pass

class Base3:
    pass

class A(metaclass=Meta1, six.with_metaclass(Meta2, Base1), Base2[int], Base3, Base4):
    pass

<the_ref>A