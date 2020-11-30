def func():
    value = "not-none"

    <caret>if value is None:  # type: ignore
        print("None")
    else:  # Regular
        print("Not none")