def func():
    value = "not-none"

    while True:
        <caret>if value is None:
            print("None")
            continue

        print(value)