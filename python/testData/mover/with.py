def temp(filepath):
    a = 1
    with open(filepath) as f:
        l = <caret>f.readlines()
        for line in l:
            a = 1
