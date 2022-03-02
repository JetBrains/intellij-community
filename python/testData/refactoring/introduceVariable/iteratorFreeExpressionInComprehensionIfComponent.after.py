y = 42

def foo():
    a = y + 3<caret>
    return [x + 1 for x in range(0, 42) if x > a]