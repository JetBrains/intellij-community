def func():
    value = "not-none"

    # pylint: disable=unused-argument
    if value is not None:
        <selection>pass</selection><caret>
    else:
        print("None")

    print(value)
