from mod import Base


class Sub(Base):
    def method(self, x: int) -> str:
        return super().method(x)
