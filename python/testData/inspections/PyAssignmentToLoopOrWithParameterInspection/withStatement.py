with open("file") as f:
    f.read()
    <warning descr="Assignment to 'for' loop or 'with' statement parameter">f</warning> = open("another file")