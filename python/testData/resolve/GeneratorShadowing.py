def _(arg):
    print(arg)

def foo():
    _("foo") # This call is underlined by the inspector as an unresolved reference
#   <ref>
    print("\n".join("bar" for _ in range(5)))

foo()
