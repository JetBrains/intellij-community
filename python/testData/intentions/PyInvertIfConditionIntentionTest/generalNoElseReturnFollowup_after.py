def func():
    value = "not-none"

    if value is not None:
        print(value)
        return

    print("None")