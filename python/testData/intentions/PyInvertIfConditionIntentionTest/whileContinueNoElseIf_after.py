def func():
    value = "not-none"

    while True:
        if value is not None:
            continue
        print("None")