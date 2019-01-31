kwargs = {'foo': 'bar'}


class Foo(object):
    def __init__(self, **kwargs):
        pass

    @classmethod
    def test(cls):
        cls(**kwargs, <error descr="Python version 2.7 does not allow keyword arguments after **expression">foo=1</error>)