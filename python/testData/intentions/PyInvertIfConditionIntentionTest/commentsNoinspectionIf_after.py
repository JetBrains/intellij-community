def func():
    value = "not-none"

    # noinspection SomeInspection
    if value is not None:
        print("Not none")
    else:
        print("None")