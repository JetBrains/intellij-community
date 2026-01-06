__author__ = 'Mikhail.Golubev'
__all__ = ['S1', 'S2']
__version__ = '0.1'

from typing_extensions import TypeAlias
from typing import TypeAlias as TA

S1_notOk = "foo"
S2_notOk = "foo.bar"
S3_notOk = "foo.bar[baz]"
too_long_string = "foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo.foo"
natural_text = "Foo is baz."
glued_string = 'foo' '.bar'

S4_notOk = f"int"
S5_notOk = u"int"
S6_notOk = b"int"

bin1_ok = int | str
bin2_ok = int | str | bool | None
bin3_ok = Union[str, bool] | None
bin4_ok = str & int
list_notOk = [int, str]

bin5_notOk: int | str = "foo"
list_none_notOk: list[int] | None = None

callable_ok = Callable[[int, str], bool]

explicit_alias1_ok: TypeAlias = 'Foo is bar.'
explicit_alias2_ok = 'Foo is bar.'  # type: TypeAlias
explicit_alias_imported_via_as_ok: TA = int | str

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
