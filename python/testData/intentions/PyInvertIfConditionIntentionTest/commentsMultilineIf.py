def func():
    value = "not-none"

    # Is none
    # If it's none
    <caret>if value is None:
        print("None")
    else:
        print("Not none")