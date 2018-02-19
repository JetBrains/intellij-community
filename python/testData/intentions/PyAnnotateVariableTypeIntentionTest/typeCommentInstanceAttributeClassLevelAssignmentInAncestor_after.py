class Base(object):
    attr = 0  # type: [int]


class MyClass(Base):
    def __init___(self):
        self.attr = 42
        self.attr