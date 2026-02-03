class Base(object):
    class_field = 1


class Child(Base):
    def f(self):
        self.class_field = 3