def f():
    a = 1
    a, result = sum_squares(a)
    print("Sum of squares: " + a + " = " + result)


def sum_squares(a_new):
    result = 0
    while a_new < 10:
        result += a_new * a_new
        a_new += 1
    return a_new, result
