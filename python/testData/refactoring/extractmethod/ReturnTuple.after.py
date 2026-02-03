def x(p_name, params):
    return bar(p_name, params), None


def bar(p_name_new, params_new):
    return p_name_new + '(' + ', '.join(params_new) + ')'