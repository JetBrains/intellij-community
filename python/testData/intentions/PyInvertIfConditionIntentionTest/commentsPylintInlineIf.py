def func():
    value = "not-none"

    <caret>if value is None:  # pylint: disable=unused-argument
        print("None")
    else:
        print("Not none")