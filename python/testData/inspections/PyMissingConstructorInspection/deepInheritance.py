class A(object):
    def __init__(self):
        print ("Constructor A was called")

class B(A):
    pass

class C(B):
    def <warning descr="Call to __init__ of super class is missed">__init__</warning>(self):
        print ("Constructor C was called")
