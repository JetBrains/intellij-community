x = 42

match x:
    case <weak_warning descr="Pattern can be simplified"><caret>int() as n</weak_warning>:
        pass
