try:
    x = f.x
except AttributeError:
    return [abs(g) for g in f]