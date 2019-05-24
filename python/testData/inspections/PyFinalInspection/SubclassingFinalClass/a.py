from b import A
class B(<warning descr="'A' is marked as '@final' and should not be subclassed">A</warning>):
    pass