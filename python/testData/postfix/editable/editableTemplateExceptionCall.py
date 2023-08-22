def e() -> BaseException:
    return BaseException()

def _():
    e().foo<caret>
