def func():
    value = "not-none"

    if value is None:
        print("None")
    else:
        if value != "not-none":
            pass
        else:
            print("Not none value")
            return True

    return False