value = 1
absent_values = [1, 2]
present_values = [3, 4]

<caret>if value is not None and value >= 1 and value <= 100 or value == 50 and value not in absent_values or value in present_values:
    print("In range 1..100")
else:
    print("Not in range 1..100")