def func():
    value = "not-none"

    # pylint: disable=unused-argument1
    if value is not None:
        <selection>pass</selection><caret>
    else:
        print("None")

    # pylint: disable=unused-argument2
    print(value)
