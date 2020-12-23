def func():
    value = "not-none"

    <caret>if value is None:
        return

    # Is not none
    print(value)