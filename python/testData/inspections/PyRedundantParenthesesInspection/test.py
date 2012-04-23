if <weak_warning descr="Remove redundant parentheses">((True or <weak_warning descr="Remove redundant parentheses">(False)</weak_warning>))</weak_warning>:
    pass
elif <weak_warning descr="Remove redundant parentheses">(True)</weak_warning>:
    pass

while <weak_warning descr="Remove redundant parentheses">(True)</weak_warning>:
    pass

try:
    foo()
except (<weak_warning descr="Remove redundant parentheses">(A)</weak_warning>):
    pass
except <weak_warning descr="Remove redundant parentheses">(A)</weak_warning> :
    pass

try:
    foo()
except (A, B):
    pass

if (A and
    B and
    C):
    pass

if <weak_warning descr="Remove redundant parentheses">("\n")</weak_warning>:
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