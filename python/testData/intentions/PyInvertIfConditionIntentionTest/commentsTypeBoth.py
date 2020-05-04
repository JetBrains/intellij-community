def func():
    value = "not-none"

    <caret>if value is None:  # type: ignore
        print("None")
    else:  # type: SomeType
        print("Not none")