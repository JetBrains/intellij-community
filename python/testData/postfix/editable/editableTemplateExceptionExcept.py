def _():
    try:
        raise Exception()
    except Exception as e:
        e.foo<caret>
