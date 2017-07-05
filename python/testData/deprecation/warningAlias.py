import warnings


Warn = DeprecationWarning


def deprecated_func():
    warnings.warn("warn", Warn)


<warning descr="warn">deprecated_func</warning>()
