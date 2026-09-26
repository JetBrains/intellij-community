def func():
    value = "not-none"

    # pylint: disable=unused-argument1
    if value is not None:
        print("Not none")
    # pylint: disable=unused-argument2
    else:
        print("None")