def bound[T: str](bar: T):
    ...


def constrained[T: (int, str)](bar: T):
    ...


def unbounded[T](bar: T):
    ...


bound(<arg1>)
constrained(<arg2>)
unbounded(<arg3>)
