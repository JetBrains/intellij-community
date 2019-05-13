class A:
    def m(self):
        """Abstract method."""
        raise NotImplementedError('Should not be called directly')


class B(A):
    def m(self):
        pass
