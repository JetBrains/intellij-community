import types

var1 = 3

def invalid_annotations(
    p1: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">eval(<warning descr="Generics should be specified through square brackets">"".join(<warning descr="Generics should be specified through square brackets">map(chr, [105, 110, 116])</warning>)</warning>)</warning>,
    p2: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">[int, str]</warning>,
    p3: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">(int, str)</warning>,
    p4: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">[int for i in <warning descr="Generics should be specified through square brackets">range(1)</warning>]</warning>,
    p5: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">{}</warning>,
    p6: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">(lambda: int)()</warning>,
    p7: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">[int][0]</warning>,
    p8: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">int if 1 < 3 else str</warning>,
    p9: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">var1</warning>,
    p10: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">True</warning>,
    p11: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">1</warning>,
    p12: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">-1</warning>,
    p13: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">int or str</warning>,
    p14: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">f"int"</warning>,
    p15: <warning descr="Type hint is invalid or refers to the expression which is not a correct type">types</warning>,
): ...