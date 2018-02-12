class Test:
    @classmethod
    def foo(cls):
        return cls._bar(), cls.__eg<caret>

    @staticmethod
    def _bar():
        pass

    @staticmethod
    def __egg():
        pass