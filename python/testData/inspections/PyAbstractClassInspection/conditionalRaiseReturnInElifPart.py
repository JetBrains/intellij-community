class A:
    @classmethod
    def test(cls, x, y):
        return None


class B(A):
    @classmethod
    def test(cls, x, y):
        if x == 1:
            print(y)
        elif x == y:
            return 1
        raise NotImplementedError


class C(B):
    pass