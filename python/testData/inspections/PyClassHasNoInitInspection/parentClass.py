__author__ = 'ktisha'
class <weak_warning descr="Class has no __init__ method">A</weak_warning>:
    def foo(self):
        self.b = 1

class <weak_warning descr="Parent A has no __init__ method">B</weak_warning>(A):
    def __init__(self):
        self.b = 2

