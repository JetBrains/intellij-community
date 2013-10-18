import mypackage.goo

def foo():
    import mypackage.bar
    mypackage.bar.dostuff()
#                  <ref>

foo()
