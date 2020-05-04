def func():
    value = "not-none"

    while True:
        print("Processing")
        if value is not None:
            continue
        print("None")