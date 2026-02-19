x = []

match x:
    case <weak_warning descr="Pattern can be simplified"><caret>list() as xs</weak_warning>:
        pass
