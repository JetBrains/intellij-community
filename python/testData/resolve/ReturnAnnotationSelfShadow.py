# PY-83181 return annotation self-shadow
def foo(object) -> object:
                   <ref1>
    return object
