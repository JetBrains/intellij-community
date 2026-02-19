def func():
    value = "not-none"

    <caret>if value is None:
        return

    # noinspection SomeInspection
    print(value)