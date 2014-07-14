try:
    try:
        print(zoo(1).foo(2))
    except:
        print(zoo(0).foo(2))
    zoo(3)
except:
    pass
