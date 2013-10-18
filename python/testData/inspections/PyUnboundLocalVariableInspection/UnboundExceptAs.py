def f():
    try:
        raise KeyError("ms")
    except KeyError as e:
        print(e) #pass
    print(<warning descr="Local variable 'e' might be referenced before assignment">e</warning>) #fail
