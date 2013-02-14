def f(name):
    <warning descr="Shadows a built-in with the same name">f<caret>ile</warning> = open(name, 'rb')
    return file.read()
