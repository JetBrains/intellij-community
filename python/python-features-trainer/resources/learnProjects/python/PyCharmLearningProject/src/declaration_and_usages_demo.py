from quadratic_equations_solver import QuadraticEquationsSolver

print("Enter 3 coefficients of full quadratic equation: ")
a, b, c = list(map(float, input().split()))
if a == 0 or b == 0 or c == 0:
    print("Any of coefficients is zero. It is not full quadratic equation.")
else:
    solver = QuadraticEquationsSolver()
    d = solver.discriminant(a, b, c)
    print("Discriminant of this equation is {.3f}".format(d))
    print("Solution is:")
    solver.solve(a, b, c)
