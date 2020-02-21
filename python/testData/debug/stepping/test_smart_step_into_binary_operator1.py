def identity(x):
    return x


z = identity(int("1") + identity((identity(identity(identity(42))))))
