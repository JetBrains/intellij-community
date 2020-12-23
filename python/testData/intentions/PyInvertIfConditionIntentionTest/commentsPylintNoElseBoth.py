def func():
    value = "not-none"

    # pylint: disable=unused-argument1
    <caret>if value is None:
        print("None")

    # pylint: disable=unused-argument2
    print(value)
