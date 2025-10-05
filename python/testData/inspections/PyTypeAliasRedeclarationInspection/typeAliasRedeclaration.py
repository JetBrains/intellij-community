type UnusedAlias = str
type <warning descr="Name 'UnusedAlias' already defined">UnusedAlias</warning> = str

type UsedAlias = int
print(UsedAlias)
type <warning descr="Name 'UsedAlias' already defined">UsedAlias</warning> = int