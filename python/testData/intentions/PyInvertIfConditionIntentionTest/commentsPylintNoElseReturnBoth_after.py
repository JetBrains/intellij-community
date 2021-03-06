def func():
    value = "not-none"

    # pylint: disable=unused-argument1
    if value is not None:
        # pylint: disable=unused-argument2
        print(value)
        return

    print("None")
