def test1(c, xs):
    if c:
        y = 1
    print(<warning descr="Local variable 'y' might be referenced before assignment">y</warning>)
    for x in xs:
        continue
        z = 1
