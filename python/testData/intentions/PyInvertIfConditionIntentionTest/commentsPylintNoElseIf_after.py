def func():
    value = "not-none"

    # pylint: disable=unused-argument
    if value is not None:
        <selection>pass</selection>
    else:
        print("None")

    print(value)
