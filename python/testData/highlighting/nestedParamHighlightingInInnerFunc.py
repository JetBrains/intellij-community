def <info descr="PY.FUNC_DEFINITION">outer_func</info>(<info descr="PY.PARAMETER">a</info>, <info descr="PY.PARAMETER">b</info>):
    def <info descr="PY.NESTED_FUNC_DEFINITION">inner_func_one</info>(<info descr="PY.PARAMETER">c</info>):
        def <info descr="PY.NESTED_FUNC_DEFINITION">inner_func_two</info>(<info descr="PY.PARAMETER">d</info>):
            x = 10
            return <info descr="PY.PARAMETER">a</info> + <info descr="PY.PARAMETER">b</info> + <info descr="PY.PARAMETER">c</info> + <info descr="PY.PARAMETER">d</info> + x

        return <info descr="PY.FUNCTION_CALL">inner_func_two</info>(4)

    return <info descr="PY.FUNCTION_CALL">inner_func_one</info>(3)