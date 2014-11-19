class GenericMeta(type):
    def __getitem__(self, args):
        pass


class Generic(object):
    __metaclass__ = GenericMeta


