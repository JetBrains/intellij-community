__author__ = 'ktisha'

def foo(x):
  return x

class A():

    @staticmethod
    @accepts(int, int)
    def my_<caret>method():
        print "Smth"