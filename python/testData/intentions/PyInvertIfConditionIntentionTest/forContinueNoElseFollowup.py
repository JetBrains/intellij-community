# See while tests, this one is for insurance only
def func():
    values = ["not-none"]

    while value in values:
        <caret>if value is None:
            print("None")
            continue

        print(value)