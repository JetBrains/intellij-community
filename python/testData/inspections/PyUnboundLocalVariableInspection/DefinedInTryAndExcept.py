def f(g):
    try:
        x = g()
    except Exception:
        x = g()
    finally:
        pass
    print(x) #pass
