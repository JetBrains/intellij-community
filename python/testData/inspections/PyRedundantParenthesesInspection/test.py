if <warning descr="Remove redundant parentheses">((True or <warning descr="Remove redundant parentheses">(False)</warning>))</warning>:
    pass
elif <warning descr="Remove redundant parentheses">(True)</warning>:
    pass

while <warning descr="Remove redundant parentheses">(True)</warning>:
    pass

try:
    foo()
except (<warning descr="Remove redundant parentheses">(A)</warning>):
    pass
except <warning descr="Remove redundant parentheses">(A)</warning> :
    pass

try:
    foo()
except (A, B):    