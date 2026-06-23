import asyncio

# PY-9778
def process():
    @asyncio.coroutine
    def <weak_warning descr="Local function 'func' is not used">func</weak_warning>():
        pass