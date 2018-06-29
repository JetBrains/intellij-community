# Stubs

class ABCMeta:
    pass


def abstractmethod(foo):
    pass


def abstractproperty(foo):
    pass


# Important:
# classes below are not presented in Python 2
# they were added just for PyDeprecationTest#testAbcDeprecatedAbstracts
# to not create separate test case for them
class abstractstaticmethod(staticmethod):
    pass


class abstractclassmethod(classmethod):
    pass