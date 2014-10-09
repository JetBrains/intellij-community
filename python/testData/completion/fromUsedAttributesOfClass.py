class Base(object):
      name = 'foo'

class MyClass(Base):
      def unique_method(self):
          pass

      def other_method(self):
          pass

x = undefined()
x.__init__()
print(x.name)
x.unique_method()
x.<caret>