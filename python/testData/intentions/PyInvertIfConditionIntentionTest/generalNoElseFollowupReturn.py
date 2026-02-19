def func():
    value = "not-none"

    <caret>if value is None:
        print("None")

    print(value)
    return True