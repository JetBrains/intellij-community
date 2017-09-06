<info descr="null">async</info> def <info descr="PY.FUNC_DEFINITION">foo</info>():
    pass

async = 1

<info descr="null">async</info> def <info descr="PY.FUNC_DEFINITION">bar</info>():
    pass


<info descr="null">async</info> def<error descr="'(' expected"><error descr="Identifier expected"> </error></error> # Incomplete<EOLError descr="':' expected"></EOLError>


<error descr="Indent expected">d</error>ef <info descr="PY.FUNC_DEFINITION">regular</info>(<info descr="PY.PARAMETER">xs</info>):

    <info descr="null">async</info> def <info descr="PY.FUNC_DEFINITION">quux</info>():
        <info descr="null">async</info> for x in xs:
            pass

        <info descr="null">async</info> with xs:
            pass

        <info descr="null">async</info> for x in xs:
            pass

    async<error descr="End of statement expected"> </error>with <info descr="PY.PARAMETER">xs</info>:
        pass

    return async
