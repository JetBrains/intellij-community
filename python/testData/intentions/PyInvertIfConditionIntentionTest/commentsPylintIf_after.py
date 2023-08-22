def func():
    value = "not-none"

    # pylint: disable=unused-argument
    if value is not None:
        print("Not none")
    else:
        print("None")