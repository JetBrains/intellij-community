def func():
    value = "not-none"

    if value is not None:
        print("Not none")
        return
        print("Unreachable not none")

    print("None")
    return
    print("Unreachable none")