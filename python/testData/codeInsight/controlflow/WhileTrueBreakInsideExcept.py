while True:
    try:
        foo = could_raise()
    except IndexError:
        break
    print(foo)