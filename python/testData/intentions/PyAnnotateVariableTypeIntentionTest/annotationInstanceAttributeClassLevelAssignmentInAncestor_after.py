class Base(object):
    attr: [int] = 0


class MyClass(Base):
    def __init___(self):
        self.attr = 42
        self.attr