def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return

    # noinspection SomeInspection
    print(value)