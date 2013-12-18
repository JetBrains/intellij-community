try:
    try:
        print(zoo(1).foo(2))
    except:
        print(zoo(0).foo(2))
except:
    zoo<caret>(3)
