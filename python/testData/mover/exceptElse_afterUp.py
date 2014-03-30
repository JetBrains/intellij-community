
try:
    print(zoo(1).foo(2))
    print<caret>(zoo(0).foo(2))        # <- move statement up here
except:
    pass
else:
    a = 1