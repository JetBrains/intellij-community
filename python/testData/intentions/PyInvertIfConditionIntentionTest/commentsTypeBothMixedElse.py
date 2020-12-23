def func():
    value = "not-none"

    <caret>if value is None:  # Regular
        print("None")
    else:  # type: SomeType
        print("Not none")