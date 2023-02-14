def some_method():
    a = b = c = d = True
    yield a == b and \
           b == c and \
           c == d
