def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return
        print("Unreachable none")

    print("Not none")
    return
    print("Unreachable not none")