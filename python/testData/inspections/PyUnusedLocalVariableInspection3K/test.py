def func1():
    var = 1

    def func2():
        <warning descr="nonlocal keyword available only since py3">nonlocal var</warning>
        var = 2

    print(var)
    func2()
    print(var)

func1()
