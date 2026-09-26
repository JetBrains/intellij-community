class A[T]:
    def __init__(self, t: T):
        self.t = t


a = A/*<# [int] #>*/(1)
