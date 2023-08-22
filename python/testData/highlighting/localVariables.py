def <info descr="PY.FUNC_DEFINITION">fun</info>():
    <info descr="PY.LOCAL_VARIABLE">local_var</info> = "hello"
    <info descr="PY.BUILTIN_NAME">print</info>(<info descr="PY.LOCAL_VARIABLE">local_var</info>)

    def <info descr="PY.NESTED_FUNC_DEFINITION">nested</info>():
        <info descr="PY.BUILTIN_NAME">print</info>(<info descr="PY.LOCAL_VARIABLE">local_var</info>)

    return