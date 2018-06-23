

def warn(c):
    with open('foo.txt', 'rb') as FILE:
        if c:
            raise ValueError
        val = 1

    return val  # raises UnboundLocalError


def ok(context):
    with context as c:
        if c:
            raise ValueError
        val = 1

    return val  # raises UnboundLocalError
