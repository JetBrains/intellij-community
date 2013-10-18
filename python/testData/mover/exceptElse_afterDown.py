
try:
    print(zoo(1).foo(2))
except:
    pass
else:
    print<caret>(zoo(0).foo(2))        # <- move statement up here
    a = 1