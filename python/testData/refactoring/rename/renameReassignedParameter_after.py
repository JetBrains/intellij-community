def cover(a, bar=1):
    item = a + bar
    bar = 1
    if a > 1:
        bar = cover(item, bar)
    return bar