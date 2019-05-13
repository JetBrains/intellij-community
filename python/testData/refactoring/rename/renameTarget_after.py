if True:
    bar = {}
else:
    bar = object()

if "a" == "b":
    bar.x = 1
    bar.save()

if "c" == "d":
    bar.y = 1
    bar.save()



