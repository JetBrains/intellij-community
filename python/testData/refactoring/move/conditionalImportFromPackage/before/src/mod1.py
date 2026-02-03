if True:
    import pkgutil
else:
    from pkg1 import mod2 as pkgutil

print(pkgutil)
