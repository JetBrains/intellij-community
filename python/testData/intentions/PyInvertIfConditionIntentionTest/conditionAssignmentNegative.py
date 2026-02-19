def get_value():
    return True

<caret>if not (value := get_value()):
    print("False")
else:
    print("True")