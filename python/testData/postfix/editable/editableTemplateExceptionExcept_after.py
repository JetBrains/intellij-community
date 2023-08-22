def _():
    try:
        raise Exception()
    except Exception as e:
        foo(e)
