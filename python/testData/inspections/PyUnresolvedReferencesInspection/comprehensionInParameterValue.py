def f(x=[i for i in [1, 2, 3]]): # i is resolved in file scope to i
    return x
