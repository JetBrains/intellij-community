a = 1
try:
    b = 2
    for x in [1, 2, 3]: # loop:8
        try:
            c = 3
            try:
                d = 4
                if x == 0:
                    break
                elif x == 1:
                    continue
                elif x == 2:
                    raise Exception()
                elif x == 3:
                    return 42
                e = 5
            finally: # f:37,s:40
                f = 6
            g = 7
        finally: # f:45,s:48
            h = 8
        i = 9
    j = 10
finally: # f:55,s:58
    k = 11
l = 12
