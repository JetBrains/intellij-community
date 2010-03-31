# makes sense for python 2.x
class A:
  def __init__(self, one):
    pass

class B(A):
  def __new__(cls, one, two):
    pass 

b = B(<arg1>"only_one") 
