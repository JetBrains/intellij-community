f = open("file.txt")
f.writelines("a")  # OK
f.<warning descr="Unresolved attribute reference 'writeliness' for class 'BinaryIO'">writeliness</warning>("a")

f = open("file.txt", "rb")
f.writelines("a")  # OK
f.<warning descr="Unresolved attribute reference 'writeliness' for class 'BinaryIO'">writeliness</warning>("a")