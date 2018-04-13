class _InitVarMeta(type):
    def __getitem__(self, params):
        return self

class InitVar(metaclass=_InitVarMeta):
    pass


def dataclass(_cls=None, *, init=True, repr=True, eq=True, order=False,
              unsafe_hash=False, frozen=False):
    pass


def fields(class_or_instance):
    pass


def asdict(obj, *, dict_factory=dict):
    pass


def astuple(obj, *, tuple_factory=tuple):
    pass


def replace(obj, **changes):
    pass