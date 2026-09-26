def func():
    value = "not-none"

    <caret>if value is None:  # type: ignore
        print("None")
        return

    print(value)