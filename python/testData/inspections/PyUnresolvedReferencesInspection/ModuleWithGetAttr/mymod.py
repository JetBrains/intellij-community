def myhiddenfunc():
    return "ok"

def __getattr__(name):
    if name == "myfunc":
        return myhiddenfunc
    raise AttributeError