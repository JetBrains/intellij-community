def test():
    print(map(str, [1, 2, 3]) + ['foo']) #pass
    print(map(lambda x: x.upper(), 'foo')) #pass
    print(filter(lambda x: x % 2 == 0, [1, 2, 3]) + [4, 5, 6]) #pass
    print(filter(lambda x: x != 'f', 'foo') + 'bar') #pass
