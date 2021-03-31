class A:
    pass

assert isinstance(A(), (int<caret>, str, list[str], bool, float, dict[str, int]))