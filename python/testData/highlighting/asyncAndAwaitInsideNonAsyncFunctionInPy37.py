def foo():
    <error descr="'await' outside async function">await</error> undefined

    <error descr="'async' outside async function">async</error> for i in undefined:
        pass

    <error descr="'async' outside async function">async</error> with undefined:
        pass

    {i <error descr="'async' outside async function">async</error> for i in undefined}