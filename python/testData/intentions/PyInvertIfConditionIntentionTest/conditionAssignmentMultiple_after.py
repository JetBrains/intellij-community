def get_value():
    return 1

if (value := get_value()) and value > 1:
    print("Greater")
else:
    print("Less or equal")