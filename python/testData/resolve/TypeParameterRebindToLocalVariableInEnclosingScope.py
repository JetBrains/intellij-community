def outer[T]():
    def inner():
        print(T)
#             <ref>

    T = -1