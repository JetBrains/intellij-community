class C(object):
    def __init__(self):
        self._x = None

    @property
    def fo<caret>o(self):
        """I'm the 'x' property."""
        return self._x

    @foo.setter
    def foo(self, value):
        self._x = value

    @foo.deleter
    def foo(self):
        del self._x
