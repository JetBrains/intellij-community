from mod import Base, default


class Sub(Base):
    def method(self, param=default):
        super().method(param)
