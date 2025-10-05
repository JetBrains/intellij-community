class A:
    <error descr="Function \"__init__\" cannot be async">async</error> def __init__(self):
        self.foo = '2'
        self.bar = '3'

a = A()
print(a.foo)