def handle(foo, **args, <error descr="multiple ** parameters are not allowed">**moreargs</error>):
    print(foo, args, moreargs)

def handle(foo, **args: int, <error descr="multiple ** parameters are not allowed">**moreargs: int</error>):
    print(foo, args, moreargs)