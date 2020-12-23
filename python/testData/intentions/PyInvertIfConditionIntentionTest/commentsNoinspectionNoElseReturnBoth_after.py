def func():
    value = "not-none"

    # noinspection SomeInspection1
    if value is not None:
        # noinspection SomeInspection2
        print(value)
        return

    print("None")