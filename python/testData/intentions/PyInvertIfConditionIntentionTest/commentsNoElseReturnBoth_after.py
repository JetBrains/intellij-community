def func():
    value = "not-none"

    # Is not none
    if value is not None:
        print(value)
        return

    # Is none
    print("None")