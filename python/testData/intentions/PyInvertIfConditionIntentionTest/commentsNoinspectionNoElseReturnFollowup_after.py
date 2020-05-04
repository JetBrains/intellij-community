def func():
    value = "not-none"

    if value is not None:
        # noinspection SomeInspection
        print(value)
        return

    print("None")