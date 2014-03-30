def somefunc():
    xx, yy = 1, 1
    gen_object = (xx for bar in lst1 for xx in bar)
    print(xx, yy)