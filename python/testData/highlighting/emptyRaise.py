<error descr="No exception to reraise">raise</error>

try:
    raise ValueError
except:
    raise


try:
    raise ValueError
finally:
    <error descr="Python version 2.7 does not support this syntax. Raise with no arguments can only be used in an except block">raise</error>


def exception_handler():
    if undefined:
        raise
    log_somehow()