def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
    else:  # type: ignore
        print("Not none")