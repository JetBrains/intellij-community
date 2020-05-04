def func():
    value = "not-none"

    <caret>if value is None:  # Is none
        print("None")
    else:  # Is not none
        print("Not none")