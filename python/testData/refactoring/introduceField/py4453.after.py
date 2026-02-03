import math

__author__ = 'wombat'
class SolverEquation:
    def demo(self):
        a = 3
        b = 25
        c = 46
        self.a = math.sqrt(b ** 2 - 4 * a * c)
        root1 = (-b + self.a) / (2 * a)
        root2 = (-b - self.a) / (2 * a)
        print(root1,root2)