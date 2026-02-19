def func():
    value = "not-none"

    if value is not None:
        print("Not none")
    # pylint: disable=unused-argument
    else:
        print("None")