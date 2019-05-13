if markup:
    try:
        return renderer
    except KeyError:
        raise Error
else:
    return body
