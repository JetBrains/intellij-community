z = lambda x, y=1, *args, **kwargs: x * y + sum(args) * kwargs.get("k", 1)
z(<arg1>1, <arg2>2, <arg3>4, <arg4>k=5)
