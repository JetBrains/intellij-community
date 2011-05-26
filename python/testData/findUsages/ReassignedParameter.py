def cover(board, lab=1):
    lab += 1
    for dx in [0, 1]:
        lab = cover(board, lab)
    return la<caret>b