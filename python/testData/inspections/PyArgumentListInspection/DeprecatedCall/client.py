import typing_extensions
import warnings


@typing_extensions.deprecated("depr")
def func(x, y):
    pass


@warnings.deprecated("depr")
def second_func(x, y):
    pass


func(10<warning descr="Parameter 'y' unfilled">)</warning>

second_func("10"<warning descr="Parameter 'y' unfilled">)</warning>
