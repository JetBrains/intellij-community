class <info descr="PY.CLASS_DEFINITION">ExampleClass</info>(<info descr="PY.BUILTIN_NAME">object</info>):
    def <info descr="PY.PREDEFINED_DEFINITION">__init__</info>(<info descr="PY.SELF_PARAMETER">self</info>):
        <info descr="PY.SELF_PARAMETER">self</info>.ex1 = 1

    def <info descr="PY.FUNC_DEFINITION">outer_test_func</info>(<info descr="PY.SELF_PARAMETER">self</info>):
        def <info descr="PY.NESTED_FUNC_DEFINITION">inner_test_func</info>():
            def <info descr="PY.NESTED_FUNC_DEFINITION">inner_test_func_lvl2</info>():
                <info descr="PY.SELF_PARAMETER">self</info>.ex1 = 10

            <info descr="PY.SELF_PARAMETER">self</info>.ex1 = 2

        return inner_test_func

    def <info descr="PY.FUNC_DEFINITION">outer_test_func_with_non_default_self_name</info>(<info descr="PY.SELF_PARAMETER">this</info>):
            def <info descr="PY.NESTED_FUNC_DEFINITION">inner_test_func</info>():
                def <info descr="PY.NESTED_FUNC_DEFINITION">inner_test_func_lvl2</info>():
                    <info descr="PY.SELF_PARAMETER">this</info>.ex1 = 10

                <info descr="PY.SELF_PARAMETER">this</info>.ex1 = 2

            return inner_test_func