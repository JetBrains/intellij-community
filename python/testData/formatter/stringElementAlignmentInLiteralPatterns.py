match x:
    case ('foo'
    'bar'):
        pass
    case ('foo'
    'bar',):
        pass
    case ['foo'
    'bar']:
        pass
    case Class('foo'
    'bar'):
        pass