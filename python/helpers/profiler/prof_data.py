def copy_fields(obj, yappi_obj):
    for k in yappi_obj._KEYS:
        if k != 'children':
            if hasattr(obj, k):
                setattr(obj, k, getattr(yappi_obj, k))

        if hasattr(yappi_obj, 'children'):
            for o in getattr(yappi_obj, 'children'):
                child = obj.children.add()
                copy_fields(child, o)

