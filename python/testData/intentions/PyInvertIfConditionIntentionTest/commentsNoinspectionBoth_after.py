def func():
    value = "not-none"

    # noinspection SomeInspection1
    if value is not None:
        print("Not none")
    # noinspection SomeInspection2
    else:
        print("None")