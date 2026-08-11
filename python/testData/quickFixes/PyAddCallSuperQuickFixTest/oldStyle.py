class A():
    def __init__(self):
        a = 1

class C(A):
    def <warning descr="Call to '__init__' of super class is missing">__ini<caret>t__</warning>(self):
        pass

    def foo(self):
        pass