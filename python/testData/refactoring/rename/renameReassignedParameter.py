def cover(a, lab=1):
    item = a + lab
    lab = 1
    if a > 1:
        lab = cover(item, lab)
    return l<caret>ab