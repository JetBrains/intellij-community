def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    # Is not none
    # If it's not none
    else:
        print("Not none")