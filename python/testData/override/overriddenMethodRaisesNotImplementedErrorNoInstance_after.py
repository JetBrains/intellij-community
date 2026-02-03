class A:
    def m(self):
        """Abstract method."""
        raise NotImplementedError


class B(A):
    def m(self):
        pass
