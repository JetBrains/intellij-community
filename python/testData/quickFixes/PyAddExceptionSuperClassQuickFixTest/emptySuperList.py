
class MyException:
    def __new__(cls, x):
        pass


def foo():
    raise <warning descr="Exception doesn't inherit from base 'Exception' class">My<caret>Exception()</warning>

