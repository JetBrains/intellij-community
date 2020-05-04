def func():
    value = "not-none"

    # noinspection SomeInspection1
    <caret>if value is None:
        print("None")
    # noinspection SomeInspection2
    else:
        print("Not none")