def func():
    value = "not-none"

    # pylint: disable=unused-argument
    <caret>if value is None:
        print("None")

    print(value)
