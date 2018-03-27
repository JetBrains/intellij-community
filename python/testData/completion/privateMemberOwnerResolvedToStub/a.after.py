class Test:
    @classmethod
    def foo(cls):
        return cls._bar(), cls.__egg()

    @staticmethod
    def _bar():
        pass

    @staticmethod
    def __egg():
        pass