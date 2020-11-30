def func():
    value = "not-none"

    if value is not None:
        print("Not none")
    # noinspection SomeInspection
    else:
        print("None")