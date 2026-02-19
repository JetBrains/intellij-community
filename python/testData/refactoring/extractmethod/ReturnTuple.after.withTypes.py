from typing import Any


def x(p_name, params):
    return bar(p_name, params), None


def bar(p_name_new, params_new) -> Any:
    return p_name_new + '(' + ', '.join(params_new) + ')'