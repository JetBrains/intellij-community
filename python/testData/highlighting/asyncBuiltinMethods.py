class A:
    <error descr="method \"__init__\" cannot be async">async</error> def __init__(self):
        pass

    <error descr="method \"__contains__\" cannot be async">async</error> def __contains__(self, value):
        pass

    async def __aiter__(self):
        pass