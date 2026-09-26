from collections import deque

L = list(range(1000))

D = {}
for i in range(1000):
    D[i] = i

S = set()

for i in range(1000):
    S.add(i)

dq = deque(range(1000))

len(dq)