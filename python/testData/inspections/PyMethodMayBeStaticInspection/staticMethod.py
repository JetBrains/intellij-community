__author__ = 'ktisha'

class Foo(object):
    @staticmethod
    def foo(param):    # <-method here should not be highlighted
        return "foo"