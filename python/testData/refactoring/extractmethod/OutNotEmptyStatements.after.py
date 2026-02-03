def f():
    a = 10
    result = 0
    result = sum_squares(a, result)
    print("Sum of squares: " + result)


def sum_squares(a_new, result_new):
    while a_new < 10:
        result_new += a_new * a_new
        a_new += 1
    return result_new
