def func(seq):
    def function(x, y):
        return (x + y) / y

    newlist = reduce(function
                     , seq)