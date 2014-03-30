kwargs = {'foo': 'bar'}


class Foo(object):

    @classmethod
    def test(cls):
        cls(**kwargs, <error descr="Cannot appear past keyword arguments or *arg or **kwarg">foo=1</error>)