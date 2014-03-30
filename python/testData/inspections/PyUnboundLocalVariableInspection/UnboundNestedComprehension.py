def f(xs):
    # vs is unbound
    return [(k, v) for v in <warning descr="Local variable 'vs' might be referenced before assignment">vs</warning>
                   for k, vs in xs.items()]
