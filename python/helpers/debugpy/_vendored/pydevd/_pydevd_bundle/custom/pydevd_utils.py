class VariableWithOffset(object):
    def __init__(self, data, offset):
        self.data, self.offset = data, offset


def eval_expression(expression, globals, locals):
    eval_func = get_eval_async_expression_in_context()
    if eval_func is not None:
        return eval_func(expression, globals, locals, False)

    return eval(expression, globals, locals)
