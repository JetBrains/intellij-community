def func():
    value = "not-none"

    if value is not None:
        print("Not none")
    else:  # type: ignore
        print("None")