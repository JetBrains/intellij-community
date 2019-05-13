
try:
    try:
        print(zoo(1).foo(2))
    except:
        pass
    print(zoo(0).foo(2))        # <- move statement up here
except:
    zoo(3)