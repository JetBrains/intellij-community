def f(name):
    <weak_warning descr="Shadows built-in name 'file'">f<caret>ile</weak_warning> = open(name, 'rb')
    return file.read()
