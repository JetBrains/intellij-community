import types

var1 = 3

def invalid_annotations(
    p1: <warning descr="Invalid type annotation">eval(<warning descr="Generics should be specified through square brackets">"".join(<warning descr="Generics should be specified through square brackets">map(chr, [105, 110, 116])</warning>)</warning>)</warning>,
    p2: <warning descr="Invalid type annotation">[int, str]</warning>,
    p3: <warning descr="Invalid type annotation">(int, str)</warning>,
    p4: <warning descr="Invalid type annotation">[int for i in <warning descr="Generics should be specified through square brackets">range(1)</warning>]</warning>,
    p5: <warning descr="Invalid type annotation">{}</warning>,
    p6: <warning descr="Invalid type annotation">(lambda: int)()</warning>,
    p7: <warning descr="Invalid type annotation">[int][0]</warning>,
    p8: <warning descr="Invalid type annotation">int if 1 < 3 else str</warning>,
    p9: <warning descr="Invalid type annotation">var1</warning>,
    p10: <warning descr="Invalid type annotation">True</warning>,
    p11: <warning descr="Invalid type annotation">1</warning>,
    p12: <warning descr="Invalid type annotation">-1</warning>,
    p13: <warning descr="Invalid type annotation">int or str</warning>,
    p14: <warning descr="Invalid type annotation">f"int"</warning>,
    p15: <warning descr="Invalid type annotation">types</warning>,
): ...