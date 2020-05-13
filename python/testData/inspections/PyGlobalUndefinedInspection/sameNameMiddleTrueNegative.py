x = [1]


def fun():
    x = False

    def fun2():
        global x
        x.append(2)