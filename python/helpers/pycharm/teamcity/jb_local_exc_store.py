import threading

_JB_PYTEST_LOCAL = threading.local()


def store_exception(exc):
    _JB_PYTEST_LOCAL.exception = exc


def get_exception():
    try:
        exception = _JB_PYTEST_LOCAL.exception
        _JB_PYTEST_LOCAL.exception = None
        return exception
    except AttributeError:
        return None
