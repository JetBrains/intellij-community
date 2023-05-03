class Test:
    @classmethod
    def foo1(cls):
        return cls._bar()

    def foo2(self, t):
        return t._bar()

    @staticmethod
    def _bar(self):
        pass