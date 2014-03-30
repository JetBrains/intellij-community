def bar(f):
    def wrapper(self, *args, **kwargs):
        print('running {cls}.{method}'.format(cls=type(self).__name__,
                                              method=f.__name__))
        return f(self, *args, **kwargs)
    return wrapper


class C(object):
    @bar
    def foo(self):  # False positive: self is used by @bar
        return 'foo'


C().foo()