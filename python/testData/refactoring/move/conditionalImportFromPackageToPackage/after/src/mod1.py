if True:
    import pkgutil
else:
    from pkg2.pkg1 import mod2 as pkgutil

print(pkgutil)
