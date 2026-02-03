from contextlib import contextmanager

MUST_REFRESH_CACHE = False


@contextmanager
def fresh_per_request_cache():
    global MUST_REFRESH_CACHE
    orig = MUST_REFRESH_CACHE
    MUST_REFRESH_CACHE = True
    try:
        yield
    finally:
        MUST_REFRESH_CACHE = orig