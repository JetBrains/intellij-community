def foo(param):
    local_var = "something"
    if param:
        del local_var
    print(<warning descr="Local variable 'local_var' might be referenced before assignment">local_var</warning>)  # deleted in 'true' branch