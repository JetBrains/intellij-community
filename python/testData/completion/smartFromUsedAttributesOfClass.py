class Base(object):
      name = 'foo'

class MyClass(Base):
      def unique_method(self):
          pass

      def other_method(self):
          pass

def func(x):
    x.__init__()
    print(x.name)
    x.unique_method()
    x.<caret>