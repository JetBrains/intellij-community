class A:
    def __init__(self, first=True, second=False): pass

class B(A):
    def __init__(self, **kwargs): A.__init__(self, first=False)

b = B(fir)