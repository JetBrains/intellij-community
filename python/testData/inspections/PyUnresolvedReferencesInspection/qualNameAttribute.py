class C:
    pass

def f(x):
    x.__qualname__ #pass

C.__qualname__ #pass
c = C()
c.__qualname__ #pass
f.__qualname__ #pass
