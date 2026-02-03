def func():
    value = "not-none"

    if value is not None:
        print(value)
        raise RuntimeError()

    print("None")
    return True