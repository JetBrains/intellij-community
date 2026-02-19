def func():
    value = "not-none"

    # pylint: disable=unused-argument
    if value is not None:
        return
    print("None")
