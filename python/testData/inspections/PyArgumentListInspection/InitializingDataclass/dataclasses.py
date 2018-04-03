class _InitVarMeta(type):
    def __getitem__(self, params):
        return self

class InitVar(metaclass=_InitVarMeta):
    pass


def dataclass(_cls=None, *, init=True, repr=True, eq=True, order=False,
              hash=None, frozen=False):
    pass


def field(*, default=_MISSING, default_factory=_MISSING, init=True, repr=True,
          hash=None, compare=True, metadata=None):
    pass