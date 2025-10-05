
class MyAwaitable:
    def __await__(self):
        yield from []
        return "done"


def fun_awaitable_imported():
    return MyAwaitable()
