def func():
    value = "not-none"

    if value is None:
        print("None")
    else:
        <caret>if value == "not-none":
            print("Not none value")
            return True

        return False

    return False