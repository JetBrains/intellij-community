class A(object):
    def some_method(self, a, b, op='add'):
        if op != 'add':
            raise NotImplementedError
        self.c = a + b


class B(A):
     pass