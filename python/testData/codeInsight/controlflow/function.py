def help(object, spacing=foo, collapse=1):
    """Выводит методы и строки документации.

    В качестве аргумента может использоваться модуль, класс, список, словарь
    или строка."""
    methodList = [method for method in dir(object) if callable(getattr(object, method))]
    processFunc = collapse and (lambda s: " ".join(s.split())) or (lambda s: s)
    print "\n".join(["%s %s" %
                      (method.ljust(spacing),
                       processFunc(str(getattr(object, method).__doc__)))
                     for method in methodList])

if __name__ == "__main__":
    print help.__doc__