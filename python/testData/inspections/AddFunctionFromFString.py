class C:
    def method(self):
        print(f'{<warning descr="Unresolved reference 'my_function'">my_<caret>function</warning>()}')