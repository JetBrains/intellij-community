def test():
    x = 1
    if isinstance(x, unicode):
        x.encode('UTF-8') #pass
