if <info descr="Remove redundant parentheses">((True or <info descr="Remove redundant parentheses">(False)</info>))</info>:
    pass
elif <info descr="Remove redundant parentheses">(True)</info>:
    pass

while <info descr="Remove redundant parentheses">(True)</info>:
    pass

try:
    foo()
except (<info descr="Remove redundant parentheses">(A)</info>):
    pass
except <info descr="Remove redundant parentheses">(A)</info> :
    pass

try:
    foo()
except (A, B):
    pass

if (A and
    B and
    C):
    pass

if <info descr="Remove redundant parentheses">("\n")</info>:
    pass