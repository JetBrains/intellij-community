class MyClass(object):
    def __init__(self, **kwargs):
        <caret>thing = kwargs.pop("stuff", None)
        print("that's all folks!")