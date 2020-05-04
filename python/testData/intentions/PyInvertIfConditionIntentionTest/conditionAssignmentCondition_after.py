def get_value():
    return 1

if (value := get_value()) <= 1:
    print("Less or equal")
else:
    print("Greater")