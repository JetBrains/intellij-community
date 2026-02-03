from b import f, g

def test(x):
    if x > 0:
        out = f()
    else:
        out = g()
    out.startswith('foo') # pass