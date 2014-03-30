def foo(f):
    x = 1
    <selection>try:
        x = f()
    except Exception:
        pass</selection>
    return x