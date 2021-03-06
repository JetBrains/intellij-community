def func():
    value = "not-none"

    if value is not None:
        <selection>pass</selection><caret>
    else:
        print("None")

    print(value)
    return True