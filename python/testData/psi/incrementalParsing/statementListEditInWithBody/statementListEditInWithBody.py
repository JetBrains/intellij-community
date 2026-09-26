def foo():
    with open("f") as f:
        data = f.read()
        print(data)
    return