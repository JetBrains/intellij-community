def expand(values1: list, values2: list):
    values1 = values2 + values1
    <weak_warning descr="Assignment can be replaced with augmented assignment">values1 = values1 + values2</weak_warning>


def expand(values1, values2):
    a = values1[0]
    b = values2[0]

    values1 = values2 + values1
    <weak_warning descr="Assignment can be replaced with augmented assignment">values1 = values1 + values2</weak_warning>


#def expand(values1, values2):
#    a = len(values1)
#    b = len(values2)
#
#    values1 = values2 + values1
#    values1 = values1 + values2
#    inspection should suggest replacement only for the second assignment


#def expand(values1, values2):
#    for a in values1:
#      print(a)
#
#    for b in values2:
#      print(b)
#
#    values1 = values2 + values1
#    values1 = values1 + values2
#    inspection should suggest replacement only for the second assignment


def expand(values1, values2):
    a = 5 in values1
    b = 5 in values2

    values1 = values2 + values1
    <weak_warning descr="Assignment can be replaced with augmented assignment">values1 = values1 + values2</weak_warning>