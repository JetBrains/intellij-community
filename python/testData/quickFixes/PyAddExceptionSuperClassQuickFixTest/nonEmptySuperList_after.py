
class A:
    pass

class MyException(A, Exception):
    def __new__(cls, x):
        pass


def foo():
    raise MyException()

