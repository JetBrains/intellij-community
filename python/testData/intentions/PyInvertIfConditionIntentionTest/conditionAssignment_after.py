def get_value():
    return True

if not (value := get_value()):
    print("False")
else:
    print("True")