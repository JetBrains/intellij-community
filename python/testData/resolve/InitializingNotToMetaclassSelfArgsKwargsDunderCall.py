class MyMeta(type):
    def __call__(cls, *args, **kwargs):
        pass

class MyClass(metaclass=MyMeta):
    pass

MyClass()
  <ref>