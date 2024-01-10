def <info descr="PY.FUNC_DEFINITION">fun</info>():
    <info descr="PY.LOCAL_VARIABLE">local_var</info> = "hello"
    <info descr="PY.BUILTIN_NAME">print</info>(<info descr="PY.LOCAL_VARIABLE">local_var</info>)

    def <info descr="PY.NESTED_FUNC_DEFINITION">nested</info>():
        <info descr="PY.BUILTIN_NAME">print</info>(<info descr="PY.LOCAL_VARIABLE">local_var</info>)
    
    for <info descr="PY.LOCAL_VARIABLE">i</info> in <info descr="PY.BUILTIN_NAME">range</info>(42):
        with <info descr="PY.BUILTIN_NAME">open</info>(f"file{<info descr="PY.LOCAL_VARIABLE">i</info>}.txt") as <info descr="PY.LOCAL_VARIABLE">f</info>:
            while <info descr="PY.LOCAL_VARIABLE">line</info> := <info descr="PY.LOCAL_VARIABLE">f</info>.<info descr="PY.METHOD_CALL">readline</info>():
                _, *<info descr="PY.LOCAL_VARIABLE">record</info> = <info descr="PY.LOCAL_VARIABLE">line</info>.<info descr="PY.METHOD_CALL">split</info>()
                <info descr="null">match</info> <info descr="PY.LOCAL_VARIABLE">record</info>:
                    <info descr="null">case</info> ["foo", <info descr="PY.LOCAL_VARIABLE">bar</info>]:
                        <info descr="PY.BUILTIN_NAME">print</info>(<info descr="PY.LOCAL_VARIABLE">bar</info>)
                