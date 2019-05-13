
class A:
    pass

class MyException(A):
    def __new__(cls, x):
        pass


def foo():
    raise <warning descr="Exception doesn't inherit from base 'Exception' class">MyExcep<caret>tion()</warning>

