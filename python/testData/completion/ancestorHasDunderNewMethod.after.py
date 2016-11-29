class A(object):
    @staticmethod
    def __new__(cls, *more):
        return super(A, cls).__new__(cls, *more)


class B(A):
    def test_method(self):
        self.test_method()