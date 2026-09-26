def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return
        print("Unreachable none")
        return False

    print("Not none")
    return
    print("Unreachable not none")
    return True