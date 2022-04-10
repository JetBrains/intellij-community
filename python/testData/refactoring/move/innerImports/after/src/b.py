from lib1 import S, K


def f(x):
    from lib1 import I
    return S(K(I))(I)(42)
