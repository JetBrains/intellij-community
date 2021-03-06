def dont_commit(x):
    for a, _b in x:
        print(a)
    for a, _b, (_c, _d) in x:
        print(a)
    for _i in range(3):
        print('hello')
