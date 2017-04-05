def func1():
    var = 1

    def func2():
        nonlocal var
        var = 2

    print(var)
    func2()
    print(var)

func1()
