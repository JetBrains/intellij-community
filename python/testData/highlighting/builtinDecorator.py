<info descr="PY.DECORATOR">@</info> <info descr="PY.DECORATOR">foo</info>
def <info descr="PY.FUNC_DEFINITION">f</info>():
    pass


class <info descr="PY.CLASS_DEFINITION">C</info>:
    <info descr="PY.DECORATOR">@</info> <info descr="PY.DECORATOR">f</info>
    def <info descr="PY.FUNC_DEFINITION">bar</info>(<info descr="PY.SELF_PARAMETER">self</info>):
        pass

    <info descr="PY.DECORATOR">@</info><info descr="PY.DECORATOR">staticmethod</info>
    def <info descr="PY.FUNC_DEFINITION">bar</info>():
        pass