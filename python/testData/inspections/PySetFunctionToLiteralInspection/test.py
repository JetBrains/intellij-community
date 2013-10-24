my_set = set()
my_set = <warning descr="Function call can be replaced with set literal">set([1,2,3])</warning>
my_set = <warning descr="Function call can be replaced with set literal">set((1,2,3))</warning>

my_set = set(var)

def set(fake=None):
  pass

my_fake_set = set()
my_fake_set = set([1,2,3])
