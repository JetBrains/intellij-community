<info descr="null">async</info> def <info descr="null">foo</info>():
    pass

async = 1

<info descr="null">async</info> def <info descr="null">bar</info>():
    pass


<info descr="null">async</info> def<error descr="'(' expected"><error descr="Identifier expected"> </error></error> # Incomplete<EOLError descr="':' expected"></EOLError>


<error descr="Indent expected">d</error>ef <info descr="null">regular</info>(<info descr="null">xs</info>):

    <info descr="null">async</info> def <info descr="null">quux</info>():
        async for x in xs:
            pass

        async with xs:
            pass

        async for x in xs:
            pass

    async with xs:
        pass

    return async
