def func():
    value = "not-none"

    <caret>if value is None:  # Is none
        print("None")
    else:
        print("Not none")