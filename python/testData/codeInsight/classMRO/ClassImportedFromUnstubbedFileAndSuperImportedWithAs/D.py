from BC import C  # No problem if D is also in BC.py


class D(C):
    def __init__(self, x):
        C.__init__(self, x)  # <- "Unexpected argument" warning for x


d = D(4)
assert d.x == 4  # runs fine
