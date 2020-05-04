def func():
    value = "not-none"

    # noinspection SomeInspection
    if value is not None:
        <selection>pass</selection>
    else:
        print("None")

    print(value)