def get_value():
    return 1

<caret>if (value := get_value()) > 1:
    print("Greater")
else:
    print("Less or equal")