from contextlib import contextmanager


@contextmanager
def mycm():
    pass
    yield
    pass


@mycm()
def decorated_func():
    pass