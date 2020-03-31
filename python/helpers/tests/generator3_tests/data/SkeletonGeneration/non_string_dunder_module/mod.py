import functools


# lxml.builder.ElementMaker follows this pattern
class MagicFactory(object):
    def __call__(self, *args, **kwargs):
        return 'foo'

    def __getattr__(self, item):
        return functools.partial(item)


magic = MagicFactory()
