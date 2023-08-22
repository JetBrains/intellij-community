import sys

for s in "abc":
    if len(s) == 1:
        continue
    sys.exit(0)
raise Exception("the end")