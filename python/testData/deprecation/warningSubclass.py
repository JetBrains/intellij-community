import warnings


class RemovedWarning(DeprecationWarning):
    pass


def deprecated_func():
    warnings.warn("removed", RemovedWarning)


<warning descr="removed">deprecated_func</warning>()
