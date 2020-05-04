def func():
    value = "not-none"

    if value is not None:  # type: ignore
        print("Not none")
    else:  # type: SomeType
        print("None")