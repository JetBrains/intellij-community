match x:
    case f'foo':
        pass
    case 'foo' f'bar' 'baz':
        pass
    case f'{x}':
        pass
    case f'{x:foo':
        pass
    case True:
        pass