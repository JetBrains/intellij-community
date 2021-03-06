def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    else:  # Is not none
        print("Not none")