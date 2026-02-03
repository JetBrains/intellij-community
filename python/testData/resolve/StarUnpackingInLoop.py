def implicit_assignment():
    seq = [(1, 2, 3), (4, 5, 6, 7)]
    for a, *bbb in seq:
        print(bbb)
#              <ref>        