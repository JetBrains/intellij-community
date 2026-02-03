def foo():
    class A:
        print(__class__)
    #           <ref>

    return A()