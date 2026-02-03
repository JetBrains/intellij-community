def func():
    value = "not-none"

    if value is not None:  # type: ignore
        print(value)
        return

    print("None")