o = object()
if callable(o):
    o(42, 3.14)
<warning descr="'o' is not callable">o(-1)</warning>
