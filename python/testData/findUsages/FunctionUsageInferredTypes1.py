class A:
    def <caret>method(self):
        pass


class B:
    def method(self):
        pass

    @staticmethod
    def get_b():
        return B()
