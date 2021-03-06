class MyClass(object):
    def __init__(self, **kwargs):
        kwargs.pop("stuff", None)
        print("that's all folks!")