def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return True

    print(value)
    raise RuntimeError()