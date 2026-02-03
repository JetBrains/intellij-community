
class MyException(Exception):
    def __new__(cls, x):
        pass


def foo():
    raise MyException()

