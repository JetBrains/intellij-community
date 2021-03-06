def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return

    # Is not none
    # If it's not none
    print(value)