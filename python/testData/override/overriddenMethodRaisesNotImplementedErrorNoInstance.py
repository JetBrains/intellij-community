class A:
    def m(self):
        """Abstract method."""
        raise NotImplementedError


class B(A):
    pass
