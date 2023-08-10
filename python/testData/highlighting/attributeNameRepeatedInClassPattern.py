match 42:
    case Class(foo=1, <error descr="Attribute name 'foo' is repeated">foo</error>=[], <error descr="Attribute name 'foo' is repeated">foo</error>=str()):
        pass
        