def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return

    # pylint: disable=unused-argument
    print(value)
