from src1 import bar as bar1
from src2 import bar  # foo uses different bar


unrelated = bar(1)

y = bar1(2)
res = 2 + y