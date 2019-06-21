def should_not_be_renamed():
    x = 1
    return x


def my_function():
    res = should_not_be_renamed()
    return res


res = should_not_be_renamed()
x = res