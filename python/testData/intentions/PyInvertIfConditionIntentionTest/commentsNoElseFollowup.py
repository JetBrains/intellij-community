def func():
    value = "not-none"

    <caret>if value is None:
        print("None")

    # Is not none
    print(value)