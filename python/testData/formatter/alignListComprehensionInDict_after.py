def foo():
    return {field.key: field for key, field in inspect.getmembers(instance)
            if isinstance(field, QueryableAttribute)
            and isinstance(field.property, ColumnProperty)
            or field.foreign_keys}
