def func():
    value = "not-none"

    if value is not None:
        print("Not none")
    else:  # pylint: disable=unused-argument
        print("None")