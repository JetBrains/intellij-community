def func():
    value = "not-none"

    if value is not None:
        # pylint: disable=unused-argument
        print(value)
