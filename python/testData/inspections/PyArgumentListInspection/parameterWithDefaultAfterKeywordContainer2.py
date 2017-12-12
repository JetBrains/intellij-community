kwargs = {'foo': 'bar'}


class Foo(object):
    def __init__(self, **kwargs):
        pass

    @classmethod
    def test(cls):
        cls(**kwargs, <error descr="Python versions < 3.5 do not allow keyword arguments after **expression">foo=1</error>)