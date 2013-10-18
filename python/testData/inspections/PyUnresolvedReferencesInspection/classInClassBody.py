class C:
    print(<error descr="Unresolved reference 'C'">C</error>) #fail
    def f(self):
        print(C) #pass