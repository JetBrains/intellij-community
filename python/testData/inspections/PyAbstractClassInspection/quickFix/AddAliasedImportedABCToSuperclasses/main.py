from abc import ABC as Base, abstractmethod


class A1(Base):
    @abstractmethod
    def m1(self):
        pass


class A<caret>2(A1):
    pass
