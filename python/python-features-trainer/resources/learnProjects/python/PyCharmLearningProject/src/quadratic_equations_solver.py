from math import sqrt


class QuadraticEquationsSolver:
    def __init__(self):
        pass

    def discriminant(self, a, b, c):
        return b * b - 4 * a * c

    # We assume all coefficients are nonzero
    # and we will find only real roots
    def solve(self, a, b, c):
        d = self.discriminant(a, b, c)
        if d < 0:
            print("No roots")
        elif d > 0:
            x1 = (-b + sqrt(d)) / (2.0 * a)
            x2 = (-b - sqrt(d)) / (2.0 * a)
            print("x1 = {.3f}, x2 = {.3f}".format(x1, x2))
        else:
            print("x = {.3f}".format((-b) / (2.0 * a)))