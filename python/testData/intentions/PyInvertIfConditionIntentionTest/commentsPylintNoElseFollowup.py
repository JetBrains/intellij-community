def func():
    value = "not-none"

    <caret>if value is None:
        print("None")

    # pylint: disable=unused-argument
    print(value)
