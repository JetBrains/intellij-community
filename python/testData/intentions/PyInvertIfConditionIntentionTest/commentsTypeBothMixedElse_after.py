def func():
    value = "not-none"

    if value is not None:  # Regular
        print("Not none")
    else:  # type: SomeType
        print("None")