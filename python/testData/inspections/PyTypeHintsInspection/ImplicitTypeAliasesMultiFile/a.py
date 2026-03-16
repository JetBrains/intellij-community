from util import *

def bad_type_aliases(
    p1: <warning descr="Invalid type annotation">BadTypeAlias1</warning>,
    p2: <warning descr="Invalid type annotation">BadTypeAlias2</warning>,
    p3: <warning descr="Invalid type annotation">BadTypeAlias3</warning>,
    p4: <warning descr="Invalid type annotation">BadTypeAlias4</warning>,
    p5: <warning descr="Invalid type annotation">BadTypeAlias5</warning>,
    p6: <warning descr="Invalid type annotation">BadTypeAlias6</warning>,
    p7: <warning descr="Invalid type annotation">BadTypeAlias7</warning>,
    p8: <warning descr="Invalid type annotation">BadTypeAlias8</warning>,
    p9: <warning descr="Invalid type annotation">BadTypeAlias9</warning>,
    p10: <warning descr="Invalid type annotation">BadTypeAlias10</warning>,
    p11: <warning descr="Invalid type annotation">BadTypeAlias11</warning>,
    p12: <warning descr="Invalid type annotation">BadTypeAlias12</warning>,
    p13: <warning descr="Invalid type annotation">BadTypeAlias13</warning>,
    p14: <warning descr="Invalid type annotation">BadTypeAlias14</warning>,
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
    p12: GoodTypeAlias12[bool],
    p13: GoodTypeAlias13
): pass
