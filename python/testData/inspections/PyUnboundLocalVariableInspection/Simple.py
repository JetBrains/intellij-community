a = 1

def foo():
    print(<warning descr="Local variable 'a' might be referenced before assignment">a</warning>) #fail
    a = 21
    print(a) #pass


foo()
print(a)

if bla_bla(): #pass
    b = 1
print(<warning descr="Name 'b' can be not defined">b</warning>) #fail
print(b) #pass
c = 1

def buzz():
    print(c) #pass

buzz()

print BLA_BLA_BLA #pass

if foo():
    d = 1
else:
    d = 2

print(d) #pass
