def func():
    value = "not-none"

    <caret>if value is None:
        print("None")
        return

    try:
        return int(value)
    except ValueError:
        raise RuntimeError("Value is not int")