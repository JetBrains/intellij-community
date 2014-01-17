for i in range(5):
    for <warning descr="Assignment to 'for' loop parameter">i</warning> in range(20, 25):
        print("Inner", i)
    print("Outer", i) 