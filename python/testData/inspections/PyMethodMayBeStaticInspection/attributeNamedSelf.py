x = object()
x.self = 42


class C:
    def <weak_warning descr="Method 'method' may be 'static'">method</weak_warning>(self):
        print(x.self)
