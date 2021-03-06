def func():
    value = "not-none"

    if value is not None:  # type: ignore
        print("Not none")
    else:  # Regular
        print("None")