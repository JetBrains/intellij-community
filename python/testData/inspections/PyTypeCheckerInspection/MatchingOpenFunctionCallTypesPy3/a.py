from foo import calcT, calcB

with open('1.txt') as file1:
    calcT(file1)
    calcB(<warning descr="Expected type 'BinaryIO', got 'TextIOWrapper[_WrappedBuffer]' instead">file1</warning>)

with open('1.txt', 'rb') as file2:
    calcT(<warning descr="Expected type 'TextIO', got 'BufferedReader' instead">file2</warning>)
    calcB(file2)