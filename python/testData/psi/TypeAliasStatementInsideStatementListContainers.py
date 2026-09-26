if True:
    type myType = int
else:
    type myType = str

for i in range(10):
    type myType = str

match num:
    case 1:
        type myType = int
    case 2:
        type myType = str