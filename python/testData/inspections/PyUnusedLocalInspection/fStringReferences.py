def simple(x):
    y = 1
    print(f"{x}{y}")


def annotations(x, y):
    def g(x: f'{x}') -> f'{y}':
        return x

    g()


def default_value(x):
    def g(x=f'{x}'):
        return x

    g()


def super_classes(x):
    class C(f'{x}'):
        pass

    C()


def qualified_names():
    <weak_warning descr="Local variable 'foo' value is not used">foo</weak_warning> = 42
    bar = undefined()
    print(f'{bar.foo}')


def nested(x):
    def g():
        print(f'{x}')
    
    g()
