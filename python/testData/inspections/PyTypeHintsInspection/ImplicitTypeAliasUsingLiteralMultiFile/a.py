from sample import HttpOk, Http400, Http404


def foo() -> HttpOk[None] | Http400 | Http404:
    pass