foo(x = y := f(x))  # INVALID
foo(x=(y := f(x)))  # Valid, though probably confusing

def foo(answer = p := 42):  # INVALID
    pass
def foo(answer=(p := 42)):  # Valid, though not great style
    pass

def foo(answer: p := 42 = 5):  # INVALID
    pass
def foo(answer: (p := 42) = 5):  # Valid, but probably never useful
    pass

(lambda: x := 1)  # INVALID
lambda: (x := 1)  # Valid, but unlikely to be useful

result_set = {a := 1}  # INVALID
result_set = {(a := 1)}

result_dict = {a := 1 : b := 2}  # INVALID
result_dict = {(a := 1) : (b := 2)}

assert a := 1  # INVALID
assert (a := 1)

l = [1, 2]
l[a := 0]  # INVALID
l[(a := 0)]

with f := open('file.txt'):  # INVALID
    pass

with (f := open('file.txt')):
    pass
