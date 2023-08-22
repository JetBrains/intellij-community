def get_value():
    return 1

<caret>if not (value := get_value()) or value <= 1:
    print("Less or equal")
else:
    print("Greater")