o = object()
if callable(o):
    o(42, 3.14)
else:
    <warning descr="'object' object is not callable">o(-1)</warning>

o(1)  # o might be callable here since this line is reachable from the callable() if branch
