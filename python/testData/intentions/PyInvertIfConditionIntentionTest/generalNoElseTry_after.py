def func():
    value = "not-none"

    if value is not None:
        try:
            return int(value)
        except ValueError:
            raise RuntimeError("Value is not int")

    print("None")