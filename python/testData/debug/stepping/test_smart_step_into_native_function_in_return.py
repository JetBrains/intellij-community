def f(s):
    s = s[::-1]
    return s.swapcase()


result = f(f(f(f(f('abcdef')))))  # breakpoint
