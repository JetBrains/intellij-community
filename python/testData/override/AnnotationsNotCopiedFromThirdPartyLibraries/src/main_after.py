from lib import Base


class Sub(Base):
    def method(self, x):
        return super().method(x)
