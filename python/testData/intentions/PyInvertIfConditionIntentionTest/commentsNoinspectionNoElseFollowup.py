def func():
    value = "not-none"

    <caret>if value is None:
        print("None")

    # noinspection SomeInspection
    print(value)