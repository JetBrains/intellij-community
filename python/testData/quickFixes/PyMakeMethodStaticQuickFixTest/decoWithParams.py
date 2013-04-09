__author__ = 'ktisha'

def foo(x):
  return x

class A():

    @accepts(int, int)
    def my_<caret>method(self):
        print "Smth"