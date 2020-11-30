def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    else:
        print("Not none")