def func():
    value = "not-none"

    <caret>if value is None:  # pylint: disable=unused-argument1
        print("None")
    else:  # pylint: disable=unused-argument2
        print("Not none")