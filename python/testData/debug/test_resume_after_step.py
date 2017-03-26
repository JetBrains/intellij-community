def foo():
    a = 1
    b = 2  # breakpoint
    c = 3
    d = 4
    e = 5  # breakpoint
    return a + b


def main():
    foo()
    t = 1
    s = 12  # breakpoint


main()
