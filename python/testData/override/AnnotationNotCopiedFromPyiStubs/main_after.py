class Base:
    def method(self, x):
        pass


class Sub(Base):
    def method(self, x):
        return super().method(x)
