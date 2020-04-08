class MyMeta(type):
    def __call__(cls, p1, p2):
        pass

class MyClass(metaclass=MyMeta):
    pass

MyClass()
  <ref>