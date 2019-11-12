class _InitVarMeta(type):
    def __getitem__(self, params):
        return self

class InitVar(metaclass=_InitVarMeta):
    pass


def field(*, default=MISSING, default_factory=MISSING, init=True, repr=True,
          hash=None, compare=True, metadata=None):
    pass