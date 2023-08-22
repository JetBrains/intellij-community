def func():
    value = "not-none"

    # noinspection SomeInspection
    <caret>if value is None:
        print("None")

    print(value)