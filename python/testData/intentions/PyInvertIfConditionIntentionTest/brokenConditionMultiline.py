def func():
    value = "not-none"

    <caret>if value is None and
            value != "not-none":
        print("None")