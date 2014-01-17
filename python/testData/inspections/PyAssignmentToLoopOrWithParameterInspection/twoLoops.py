for i in range(5):
    for <warning descr="Assignment to 'for' loop or 'with' statement parameter">i</warning> in range(20, 25):
        print("Inner", i)
    print("Outer", i) 