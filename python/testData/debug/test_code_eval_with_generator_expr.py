from __future__ import print_function

TFN = [True, False, None]
for q in TFN:
    gen = (c for c in TFN if c == q)
    lcomp = [c for c in TFN if c == q]
    print(list(gen), "\t", list(lcomp))

print("BREAK HERE")
