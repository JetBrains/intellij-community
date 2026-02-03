def func():
    value = "not-none"

    if value is not None:  # pylint: disable=unused-argument1
        print("Not none")
    else:  # pylint: disable=unused-argument2
        print("None")