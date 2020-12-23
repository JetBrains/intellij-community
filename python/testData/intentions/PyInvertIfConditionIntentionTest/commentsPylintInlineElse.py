def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    else:  # pylint: disable=unused-argument
        print("Not none")