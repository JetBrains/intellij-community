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
    pass

if (A and
    B and
    C):
    pass

if <warning descr="Remove redundant parentheses">("\n")</warning>:
    pass

result = (
    "int line1 = 1;\n"
    "\n"
    "int line2 = 2;\n"
)

#PY-2310
if (A and
    B):
    print

var = '<input type="submit" value="Yes, Delete this event."/></form>' % (
event_id)