def func():
    value = "not-none"

    # noinspection SomeInspection
    if value is not None:
        return
    print("None")