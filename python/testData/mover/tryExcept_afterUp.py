
try:
    try:
        print(zoo(1).foo(2))
        print(zoo(0).foo(2))        # <- move statement up here
    except:
        pass
except:
    zoo(3)