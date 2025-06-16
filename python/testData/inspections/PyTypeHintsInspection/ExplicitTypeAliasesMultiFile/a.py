from util import *

def bad_type_aliases(
    p1: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias1</warning>,
    p2: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias2</warning>,
    p3: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias3</warning>,
    p4: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias4</warning>,
    p5: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias5</warning>,
    p6: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias6</warning>,
    p7: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias7</warning>,
    p8: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias8</warning>,
    p9: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias9</warning>,
    p10: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias10</warning>,
    p11: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias11</warning>,
    p12: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">BadTypeAlias12</warning>,
): pass

def good_type_aliases(
    p1: GoodTypeAlias1,
    p2: GoodTypeAlias2,
    p3: GoodTypeAlias3,
    p4: GoodTypeAlias4[int],
    p5: GoodTypeAlias5[str],
    p6: GoodTypeAlias6[int, str],
    p7: GoodTypeAlias7,
    p8: GoodTypeAlias8[str],
    p9: GoodTypeAlias9[[str, str], None],
    p10: GoodTypeAlias10,
    p11: GoodTypeAlias11,
    p12: GoodTypeAlias12,
    p13: GoodTypeAlias13,
    p14: GoodTypeAlias14,
    p15: GoodTypeAlias15,
): pass
