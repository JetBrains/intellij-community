from foo import calcT, calcB

with open('1.txt') as file1:
    calcT(<warning descr="Expected type 'TextIO', got 'BinaryIO' instead">file1</warning>)
    calcB(file1)

with open('1.txt', 'rb') as file2:
    calcT(<warning descr="Expected type 'TextIO', got 'BinaryIO' instead">file2</warning>)
    calcB(file2)