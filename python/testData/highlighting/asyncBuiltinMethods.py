class A:
    <error descr="function \"__init__\" cannot be async">async</error> def __init__(self):
        pass

    <error descr="function \"__contains__\" cannot be async">async</error> def __contains__(self, value):
        pass

    async def __aiter__(self):
        pass

    async def __call__(self, *args, **kwargs):
        pass