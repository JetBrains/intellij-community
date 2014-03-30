def foo1():
    a = 1
    try:
        for i in range(10):
            pass
    except Exception:
        pass
    finally:
        b = a #pass


def foo2():
    a = 1
    try:
        for i in range(10):
            pass
    except Exception:
        c = a #pass
    finally:
        b = a #pass
