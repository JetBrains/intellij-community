from abc import abstractmethod

class A1:
    @abstractmethod
    def __init__(self):
        pass


class A2:
    @abstractmethod
    def __init__(self):
        pass


class B1:
    def __init__(self):
        pass


class B2:
    pass


class C1(A1):
    def __init__(self):
        pass


class C2(A1, A2):
    def __init__(self):
        pass


class C3(A1, B1):
    def <warning descr="Call to __init__ of super class is missed">__init__</warning>(self):
        pass


class C4(B1, A1):
    def <warning descr="Call to __init__ of super class is missed">__init__</warning>(self):
        pass


class D(B2, A1):
    def __init__(self):
        pass