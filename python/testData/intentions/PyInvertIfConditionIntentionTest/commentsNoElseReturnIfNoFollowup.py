def func():
    value = "not-none"

    # Is none
    <caret>if value is None:
        print("None")
        return