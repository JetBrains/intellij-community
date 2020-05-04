def func():
    value = "not-none"

    # type: ignore
    if value is not None:
        print(value)
        return

    print("None")