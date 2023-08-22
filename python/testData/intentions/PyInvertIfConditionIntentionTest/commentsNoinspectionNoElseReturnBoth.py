def func():
    value = "not-none"

    # noinspection SomeInspection1
    <caret>if value is None:
        print("None")
        return

    # noinspection SomeInspection2
    print(value)