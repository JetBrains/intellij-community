__author__ = 'Mikhail.Golubev'
__all__ = ['S1', 'S2']
__version__ = '0.1'

S1_ok = "foo"
S2_ok = "foo.bar"
S3_ok = "foo.bar[baz]"
too_long_string = "foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo"
natural_text = "Foo is baz."
glued_string = 'foo' '.bar'

# Such expressions are kept as qualified expressions in PyTargetExpressionStub 
# with initializer type of ReferenceExpression instead of custom stubs for
# typing aliases
plain_ref = foo.bar.baz
illegal_ref = foo[42].bar.baz

T1_ok = TypeVar('T1')
T2_ok = typing.TypeVar('T2')
T3 = func()

global_list = [1, 2, 3]
global_tuple = (1, 2, 3)

for for_counter in range(10):
    pass

xs_comp = [comp_counter for comp_counter in range(10)]

multi_assign1 = multi_assign2 = Any
unpack1, unpack2 = Any
complex.ref = Any

illegal_generic1 = table[table[0].foo]
illegal_generic2 = Dict[(str, int)]
illegal_generic3 = Tuple[int, 3]
illegal_generic4 = Optional[func()]


class C:
    class_attr = Any

    def __init__(self):
        self.inst_attr = Any
