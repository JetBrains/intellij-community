def bad_autoformat_example():
    a = 5
    b = 10
    print a, b
    (a, b) = b, a
    print a, b
    a, b = b, a
    print a, b
