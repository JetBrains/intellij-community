class C:
    def method(self):
        print(f'{<error descr="Unresolved reference 'my_function'">my<caret>_function</error>()}')