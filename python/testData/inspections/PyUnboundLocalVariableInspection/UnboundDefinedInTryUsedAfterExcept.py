def a2():
    try:
        a = 2
    except Exception:
        pass
    print(<warning descr="Local variable 'a' might be referenced before assignment">a</warning>) #fail
