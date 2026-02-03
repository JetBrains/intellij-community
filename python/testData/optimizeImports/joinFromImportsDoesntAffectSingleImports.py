# Optimize imports should not reformat unaffected statements
from module1 import a, b

from module2 import A, B

print(A, B, a, b)
