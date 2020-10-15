def func():
    value = "not-none"

    while True:
        if value is not None:
            print(value)
            continue

        print("None")
        break