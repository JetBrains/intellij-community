def func():
    value = "not-none"

    while True:
        print("Processing")
        <caret>if value is None:
            print("None")