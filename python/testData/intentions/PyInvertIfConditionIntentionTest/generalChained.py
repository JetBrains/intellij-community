def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    elif value == "not-none":
        print("Not none")