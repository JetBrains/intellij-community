for x in []:
    def foo(x):
        <error descr="'break' outside loop">break</error>

for x in [1, 2, 3]:
    pass
else:
    <error descr="'break' outside loop">break</error>

while True:
    pass
else:
    <error descr="'break' outside loop">break</error>