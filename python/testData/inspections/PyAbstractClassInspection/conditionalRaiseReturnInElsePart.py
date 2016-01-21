class A:
    @classmethod
    def test(cls, param):
        return None


class B(A):
    @classmethod
    def test(cls, param):
        if param == 1:
            print(param)
        else:
          return 1
        raise NotImplementedError


class C(B):
    pass