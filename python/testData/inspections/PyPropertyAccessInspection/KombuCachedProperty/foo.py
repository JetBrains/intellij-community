from kombu.utils import cached_property

class Foo:
    def __init__(self):
        pass
    @cached_property
    def foo(self):
        pass