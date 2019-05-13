import math

__author__ = 'wombat'
class SolverEquation:
    def demo(self):
        a = 3
        b = 25
        c = 46
        root1 = (-b + <selection>math.sqrt(b ** 2 - 4 * a * c)</selection>) / (2*a)
        root2 = (-b - math.sqrt(b ** 2 - 4 * a * c)) / (2*a)
        print(root1,root2)