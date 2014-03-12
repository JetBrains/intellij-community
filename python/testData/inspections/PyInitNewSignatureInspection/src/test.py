class B(object):
  def __init__(self): # error
    pass

  def __new__(cls, x, y): # error
    pass

class A1(B):
    pass

class A2(A1):
    def __new__(cls, a): # error
       pass

    
class A3(A2):
    def __new__(cls, *a): # ok
       pass

class C(object):
    def __new__(cls, *args, **kwargs):
        pass
    
class C1(C):
    def __init__(self, a): # OK
        pass

