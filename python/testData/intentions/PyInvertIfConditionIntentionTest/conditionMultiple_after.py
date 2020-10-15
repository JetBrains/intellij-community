value = 1
absent_values = [1, 2]
present_values = [3, 4]

if (value is None or value < 1 or value > 100) and (
        value != 50 or value in absent_values) and value not in present_values:
    print("Not in range 1..100")
else:
    print("In range 1..100")