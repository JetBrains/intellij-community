def func():
    value = "not-none"

    # Is none
    # If it's none
    <caret>if value is None:
        print("None")

    # Is not none
    # If it's not none
    print(value)