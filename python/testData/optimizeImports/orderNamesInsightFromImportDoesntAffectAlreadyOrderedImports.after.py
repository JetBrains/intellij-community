# Optimize Imports should not re-create imports statements that are in order
from module1 import a, b, c

from module2 import A, B, C

print(A, B, C, a, b, c)
