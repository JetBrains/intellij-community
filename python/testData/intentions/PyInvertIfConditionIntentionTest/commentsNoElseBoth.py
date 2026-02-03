def func():
    value = "not-none"

    # Is none
    <caret>if value is None:
        print("None")

    # Is not none
    print(value)