class C:
    def m(self):
        return 1


def func():
    return 2


X = 1

Y = X * func() + C().m()