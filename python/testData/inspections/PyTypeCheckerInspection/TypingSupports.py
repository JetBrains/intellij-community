import typing


def check_complex(p: typing.SupportsComplex):
    print(p.__complex__())


class A:
    def __int__(self):
        return 5


    def __float__(self):
        return 5.0


    def __complex__(self):
        return complex(5.0, 0.0)

    def __bytes__(self):
        return b'bytes'

    def __abs__(self):
        return 5

    def __round__(self, n=None):
        return 5


a = A()
print(int(a))
print(float(a))
check_complex(a)
print(bytes(a))
print(abs(a))
print(round(a))

