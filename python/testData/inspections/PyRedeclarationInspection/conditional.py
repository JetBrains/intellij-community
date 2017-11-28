def test_conditional(c):
    def foo():
        pass

    if c:
        def foo():
            pass

    try:
        def foo():
            pass
    except:
        pass