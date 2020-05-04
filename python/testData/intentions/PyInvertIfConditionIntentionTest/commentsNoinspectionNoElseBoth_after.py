def func():
    value = "not-none"

    # noinspection SomeInspection1
    if value is not None:
        <selection>pass</selection>
    else:
        print("None")

    # noinspection SomeInspection2
    print(value)