from abc import abstractmethod
class A1(object):
    def __init__(self):
      self.a = "Something"

    @abstractmethod
    def my_method(self):
        print self.a

class B(A1):
    def my_method(self):
        <selection>pass</selection>

    def __iter__(self): pass
